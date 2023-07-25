package ethan.entelect.textract.demo.util;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.services.textract.model.Block;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
public class KeyValueSet {
    private Block key;
    private Block value;
    private LinkedList<Block> keyWords;
    private LinkedList<Block> valueWords;

    public String keyWordsToString() {
        if(getKeyWordList().isEmpty()) return "";
        return getKeyWordList().stream()
                .filter(Objects::nonNull)
                .map(block -> block.text() == null ? "" : block.text())
                .collect(Collectors.joining(" "));
    }

    public String valueWordsToString() {
        if(getValueWordList().isEmpty()) return "";
        return getValueWordList().stream()
                .filter(Objects::nonNull)
                .map(block -> block.text() == null ? "" : block.text())
                .collect(Collectors.joining(" "));
    }

    public void addKeyWord(Block word) {
        getKeyWordList().add(word);
    }

    public void addValueWord(Block word) {
        getValueWordList().add(word);
    }

    private LinkedList<Block> getKeyWordList() {
        if (keyWords == null) keyWords = new LinkedList<>();
        return keyWords;
    }

    private LinkedList<Block> getValueWordList() {
        if (valueWords == null) valueWords = new LinkedList<>();
        return valueWords;
    }

    public boolean isComplete() {
        return key != null && value != null;
    }
}
