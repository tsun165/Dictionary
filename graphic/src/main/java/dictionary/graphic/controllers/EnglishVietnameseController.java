package dictionary.graphic.controllers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.math3.util.Pair;
import org.controlsfx.control.Notifications;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import dictionary.base.Dictionary;
import dictionary.base.Example;
import dictionary.base.Explain;
import dictionary.base.Word;
import dictionary.base.api.SpeechToTextOfflineAPI;
import dictionary.base.api.SpeechToTextOnlineAPI;
import dictionary.base.api.TextToSpeechOfflineAPI;
import dictionary.base.database.DictionaryDatabase;
import dictionary.base.utils.RecordManager;
import dictionary.base.utils.Utils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;

class LazyLoadManager {
    /**
     * If the number of suggestions is higher than this constant,
     * the suggestions will be lazy-loaded.
     */
    public static final int LAZY_LOAD_THRESHOLD = 50;

    private final ListView<Pair<String, String>> suggestedWords;

    private ArrayList<Pair<String, String>> allSuggestions;

    private boolean isLazyLoadInEffect;

    public LazyLoadManager(ListView<Pair<String, String>> suggestedWords) {
        this.suggestedWords = suggestedWords;
        this.allSuggestions = new ArrayList<>();
        this.isLazyLoadInEffect = false;
    }

    /**
     * Call this function to update the suggestions,
     * and perform lazy load when necessary.
     *
     * @param _allSuggestions All suggestions looked up.
     */
    public void updateSuggestionsFromNonGUIThread(ArrayList<Pair<String, String>> _allSuggestions) {
        Platform.runLater(() -> {
            updateSuggestionsFromGUIThread(_allSuggestions);
        });
    }

    /**
     * Force the suggestion list view to contain
     * all entries which was previously set by
     * `updateSuggestionsThreadSafe()`. This is
     * the "load" step in "lazy load".
     */
    public void loadAllSuggestionsFromNonGUIThread() {
        Platform.runLater(() -> {
            loadAllSuggestionsFromGUIThread();
        });
    }

    public void updateSuggestionsFromGUIThread(ArrayList<Pair<String, String>> _allSuggestions) {
        allSuggestions = _allSuggestions;
        if (allSuggestions.size() > LAZY_LOAD_THRESHOLD) {
            isLazyLoadInEffect = true;
            updateSuggestionsInView(
                    new ArrayList<Pair<String, String>>(
                            // list.subList() create a VIEW in the list, not an actual new list.
                            allSuggestions.subList(0, LAZY_LOAD_THRESHOLD)));
        } else {
            loadAllSuggestionsFromGUIThread();
        }
    }

    public void loadAllSuggestionsFromGUIThread() {
        isLazyLoadInEffect = false;
        updateSuggestionsInView(allSuggestions);
    }

    private void updateSuggestionsInView(ArrayList<Pair<String, String>> suggestions) {
        suggestedWords.setItems(
                FXCollections.observableList(suggestions));
    }
}

public class EnglishVietnameseController {
    @FXML
    private AnchorPane rootPane;

    @FXML
    private Button speech;

    @FXML
    private Button speechToTextButton;

    @FXML
    private Label wordField;
    @FXML
    private Label pronounceField;
    @FXML
    private VBox explainField;

    @FXML
    private TextField searchBar;

    private Thread recordThread;
    private Thread speechToTextThread;

    @FXML
    private ListView<Pair<String, String>> suggestedWords;

    private final ExecutorService searchWordExecutor = Executors.newSingleThreadExecutor();

    private Future<?> searchWordFuture = null;

    private LazyLoadManager lazyLoadManager;

    private FontAwesomeIconView speechToTextIcon;

    private Boolean isProcessingSpeechToText = false;

    private ResourceBundle bundle = ResourceBundle.getBundle("languages.language",
            SceneController.getInstance().getLocale());

    @FXML
    private void initialize() {
        // suggestedWords is invalid until this function is called.
        // Therefore, we don't instantiate LazyLoadManager in the
        // constructor nor in its in-class definition, but here.
        lazyLoadManager = new LazyLoadManager(suggestedWords);

        suggestedWords.setCellFactory((ListView<Pair<String, String>> list) -> {
            ListCell<Pair<String, String>> cell = new ListCell<>() {
                @Override
                public void updateItem(Pair<String, String> p, boolean empty) {
                    super.updateItem(p, empty);
                    if (!empty) {
                        setText(p.getFirst());
                    } else {
                        setText("");
                    }
                }
            };

            cell.setOnMouseClicked((MouseEvent event) -> {
                pickCurrentSuggestion();
            });

            cell.setOnTouchReleased((TouchEvent event) -> {
                pickCurrentSuggestion();
            });

            return cell;
        });

        suggestedWords.setOnKeyPressed((KeyEvent event) -> {
            switch (event.getCode()) {
                case ENTER:
                    pickCurrentSuggestion();
                    break;

                case UP:
                case KP_UP:
                    if (suggestedWords.getSelectionModel().getSelectedIndex() == 0) {
                        focusOnSearchBar();
                        suggestedWords.getSelectionModel().clearSelection();
                    }
                    break;

                case DOWN:
                case KP_DOWN:
                    if (suggestedWords.getSelectionModel().getSelectedIndex() == suggestedWords.getItems().size() - 1) {
                        lazyLoadManager.loadAllSuggestionsFromGUIThread();
                    }
            }
        });

        suggestedWords.addEventHandler(ScrollEvent.SCROLL, (ScrollEvent event) -> {
            // suggestedWords is a ListView object, which, under our investigation,
            // seems to capture ALL mouse scroll events and handle them itself
            // internally. However, when the scrolling exceeds boundary (i.e. when
            // the user continue to scroll up when the list is already at top, or
            // when the user continue to scroll down when the list is already at
            // bottom), the scroll event is no longer handled as such, and instead,
            // we could capture the event here. By getting deltaY, we can tell whether
            // we are at the top or bottom of the list. A negative deltaY tells us
            // that the list is reached bottom, so lazy-loading should be performed
            // then.
            if (event.getDeltaY() < 0) {
                lazyLoadManager.loadAllSuggestionsFromGUIThread();
            }
        });
    }

    private void showDetail(String wordID) {
        try {

            DictionaryDatabase database = Dictionary.getInstance().getDatabase();
            Word word = database.getWordByWordID(wordID);
            explainField.getChildren().clear();
            speech.setDisable(false);
            speech.setVisible(true);

            wordField.setText(word.getWord());
            pronounceField.setText('[' + word.getPronunce() + ']');

            ArrayList<Explain> explains = database.getExplainsByWordID(word.getWordID());
            for (Explain explain : explains) {
                Label type = new Label(explain.getType() + "\n");
                Label meaning = new Label("\t" + explain.getMeaning() + "\n");
                type.getStyleClass().add("level1");
                type.setWrapText(true);
                meaning.getStyleClass().add("level2");
                meaning.setWrapText(true);
                explainField.getChildren().add(type);
                explainField.getChildren().add(meaning);

                ArrayList<Example> examples = database.getExamples(explain.getExplainID());
                for (Example example : examples) {
                    Label exampleText = new Label("\t\t" + example.getExample() + "\n");
                    exampleText.getStyleClass().add("level3");
                    exampleText.setWrapText(true);
                    explainField.getChildren().add(exampleText);
                }
            }
        } catch (SQLException e) {
            wordField.setText(null);
            explainField.getChildren().clear();
            Label error = new Label("Đã có lỗi xảy ra");
            explainField.getChildren().add(error);
        }
    }

    @FXML
    private void onPlayAudioButton() {
        Thread thread = new Thread(() -> TextToSpeechOfflineAPI.getTextToSpeech(wordField.getText()));
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onSearchBarInputTextChanged() {
        updateSuggestions();
    }

    @FXML
    private void onSearchBarKeyPressed(KeyEvent event) {
        stopSpeechToTextProcess();
        switch (event.getCode()) {
            case ENTER:
                pickBestMatch();
                break;

            case DOWN:
            case KP_DOWN:
                useSuggestions();
                break;
        }
    }

    /**
     * Updates word suggestions.
     */
    private void updateSuggestions() {
        if (searchWordFuture != null && !searchWordFuture.isDone()) {
            searchWordFuture.cancel(true);
        }
        final String searchString = searchBar.getText();
        searchWordFuture = searchWordExecutor.submit(() -> {
            try {
                final ArrayList<Pair<String, String>> wordPairs = Dictionary.getInstance().lookup(searchString);
                lazyLoadManager.updateSuggestionsFromNonGUIThread(wordPairs);
            } catch (Exception e) {
                Platform.runLater(() -> handleException(e));
            }
        });
    }

    /**
     * This function gets called when the user no longer
     * types in the word manually, and instead wants to
     * select one of the suggestions.
     */
    private void useSuggestions() {
        if (suggestedWords.getItems().isEmpty()) {
            return;
        }
        suggestedWords.requestFocus();
        suggestedWords.getSelectionModel().select(0);
    }

    /**
     * Loads the currently-selected suggestion into the
     * search bar, and display its word's details.
     */
    private void pickCurrentSuggestion() {
        Pair<String, String> p = suggestedWords.getSelectionModel().getSelectedItem();
        if (p == null) {
            return;
        }

        try {
            showDetail(p.getSecond());
        } catch (Exception e) {
            handleException(e);
        }

        searchBar.setText(p.getFirst());
        focusOnSearchBar();
    }

    /**
     * Shows details of the best-match word, i.e. the word that is on top
     * of search results (suggestions).
     */
    private void pickBestMatch() {
        if (suggestedWords.getItems().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(String.format("Không tìm thấy từ tương tự \'%s\'", searchBar.getText()));
            alert.setContentText("Vui lòng nhập lại từ đúng hoặc thêm từ mới.");
            alert.showAndWait();
            return;
        }
        suggestedWords.getSelectionModel().select(0);
        pickCurrentSuggestion();
    }

    private void focusOnSearchBar() {
        searchBar.requestFocus();
        searchBar.deselect();
        searchBar.positionCaret(searchBar.getText().length());
    }

    private static void handleException(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText("Lỗi: " + e.getMessage());
        alert.setContentText("Vui lòng thử lại sau");
        alert.showAndWait();
    }

    private void stopSpeechToTextProcess() {
        if (speechToTextThread != null && speechToTextThread.isAlive()) {
            isProcessingSpeechToText = false;
            speechToTextThread.interrupt(); // Gracefully interrupt the thread
            updateSpeechToTextButton();
        }
    }

    private void updateSpeechToTextButton() {
        Platform.runLater(() -> {
            if (isProcessingSpeechToText) {
                ProgressIndicator spinner = new ProgressIndicator();
                speechToTextButton.setGraphic(spinner);
                speechToTextButton.getStyleClass().remove("accent");

                speechToTextButton.setOnAction(event -> {
                    isProcessingSpeechToText = false;
                    stopSpeechToTextProcess();
                });
            } else {
                speechToTextIcon = new FontAwesomeIconView(FontAwesomeIcon.MICROPHONE);
                speechToTextIcon.setSize("20px");
                speechToTextButton.setOnAction(event -> speechToText());
                speechToTextIcon.getStyleClass().add("ikonli-font-icon");
                speechToTextButton.getStyleClass().add("accent");
                speechToTextButton.setGraphic(speechToTextIcon);
            }
        });
    }

    @FXML
    private void speechToText() {
        final String speechFile = "speechToText.mp3";

        if (!RecordManager.isRecording()) {
            Notifications.create()
                    .owner(rootPane)
                    .text(bundle.getString("start_record"))
                    .showInformation();

            speechToTextButton.getStyleClass().add("danger");
            recordThread = new Thread(() -> {
                RecordManager.startRecording(speechFile);
            });

            recordThread.setDaemon(true);
            recordThread.start();
        } else {
            RecordManager.stopRecording();
            speechToTextButton.getStyleClass().remove("danger");

            Notifications.create()
                    .owner(rootPane)
                    .text(bundle.getString("stop_record"))
                    .showInformation();

            isProcessingSpeechToText = true;
            updateSpeechToTextButton();

            speechToTextThread = new Thread(() -> {
                try {
                    String searchResult;
                    if (Utils.isNetworkConnected()) {
                        searchResult = SpeechToTextOnlineAPI.getSpeechToText(speechFile);
                    } else {
                        searchResult = SpeechToTextOfflineAPI.getSpeechToText(speechFile);
                    }

                    Platform.runLater(() -> searchBar.setText(searchResult));
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isProcessingSpeechToText = false;
                    updateSpeechToTextButton();
                }
            });

            speechToTextThread.setDaemon(true);
            speechToTextThread.start();
        }
    }
}