package dictionary.cli.commands;

import dictionary.base.api.TextToIpaAPI;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "ipa", description = "Get ipa")
public class DictionaryTextToIpa implements Runnable {
    @Parameters(index = "0", description = "Text to be created ipa")
    private String textToCreatedIpa;

    @Override
    public void run() {
        try {
            System.out.println(TextToIpaAPI.textToIPA(textToCreatedIpa));
        } catch (Exception e) {
            System.err.println("Error converting text to IPA: " + e.getMessage());
        }
    }
}
