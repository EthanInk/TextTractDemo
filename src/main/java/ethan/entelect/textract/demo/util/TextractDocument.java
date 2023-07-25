package ethan.entelect.textract.demo.util;

import software.amazon.awssdk.services.textract.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


class Word {
    Block Block;
    float Confidence;
    Geometry Geometry;
    String Id;
    String Text;

    Word(Block block, List<Block> blocks) {
        this.Block = block;
        this.Confidence = block.confidence();
        this.Geometry = block.geometry();
        this.Id = block.id();
        this.Text = block == null ? "" : block.text();
    }

    @Override
    public String toString() {
        return Text;
    }
}

public class TextractDocument {
    private final List<Block> blockMap = new ArrayList<>();
    private final List<List<Block>> documentPages = new ArrayList<>();
    List<GetDocumentAnalysisResponse> ResponsePages;
    List<Page> Pages;

    public TextractDocument(List<GetDocumentAnalysisResponse> responses) {
        this.Pages = new ArrayList<>();
        this.ResponsePages = responses;

        this.ParseDocumentPagesAndBlockMap();
        this.Parse();
    }

    private void ParseDocumentPagesAndBlockMap() {
        List<Block> documentPage = null;
        for (GetDocumentAnalysisResponse page : ResponsePages) {
            for (Block block : page.blocks()) {
                blockMap.add(block);

                if (block.blockType().equals(BlockType.PAGE)) {
                    if (documentPage != null) {
                        this.documentPages.add(documentPage);
                    }
                    documentPage = new ArrayList<>();
                    documentPage.add(block);
                } else {
                    documentPage.add(block);
                }
            }
        }

        if (documentPage != null) {
            this.documentPages.add(documentPage);
        }
    }

    private void Parse() {
        for (List<Block> documentPage : documentPages) {
            Page page = new Page(documentPage, this.blockMap);
            this.Pages.add(page);
        }
    }

    Block GetBlockById(String blockId) {
        return blockMap.stream().filter(x -> x.id().equals(blockId)).findFirst().orElse(null);
    }

    List<List<Block>> getPageBlocks() {
        return this.documentPages;
    }
}

class Table {
    List<Row> Rows;
    Block Block;
    float Confidence;
    Geometry Geometry;
    String Id;

    Table(Block block, List<Block> blocks) {
        this.Block = block;
        this.Confidence = block.confidence();
        this.Geometry = block.geometry();
        this.Id = block.id();
        this.Rows = new ArrayList<>();
        int ri = 1;
        Row row = new Row();

        List<Relationship> relationships = block.relationships();
        if (relationships != null && relationships.size() > 0) {
            for (Relationship r : relationships) {
                if (r.type().equals(RelationshipType.CHILD)) {
                    for (String id : r.ids()) {
                        Block cellBlock = blocks.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
                        if (cellBlock != null) {
                            Cell cell = new Cell(cellBlock, blocks);
                            if (cell.RowIndex > ri) {
                                this.Rows.add(row);
                                row = new Row();
                                ri = cell.RowIndex;
                            }
                            row.Cells.add(cell);
                        }
                    }
                    if (row != null && row.Cells.size() > 0)
                        this.Rows.add(row);
                }
            }
        }
    }

    @Override
    public String toString() {
        List<String> result = new ArrayList<>();
        result.add(String.format("Table%n====%n"));
        for (Row r : this.Rows) {
            result.add(String.format("Row%n====%n%s%n", r));
        }
        return result.stream().map(Object::toString).collect(Collectors.joining(", "));
    }
}

class SelectionElement {
    float Confidence;
    Geometry Geometry;
    String Id;
    String SelectionStatus;

    SelectionElement(Block block, List<Block> blocks) {
        this.Confidence = block.confidence();
        this.Geometry = block.geometry();
        this.Id = block.id();
        this.SelectionStatus = block.selectionStatusAsString();
    }
}

class Row {
    List<Cell> Cells;

    Row() {
        this.Cells = new ArrayList<>();
    }

    @Override
    public String toString() {
        List<String> result = new ArrayList<>();
        for (Cell c : this.Cells) {
            result.add(String.format("[%s]", c));
        }
        return result.stream().map(Object::toString).collect(Collectors.joining(", "));
    }
}

class Page {
    List<Block> Blocks;
    String Text;
    List<Line> Lines;
    Form Form;
    List<Table> Tables;
    List<Object> Content;
    NewGeometry Geometry;
    String Id;

    Page(List<Block> blocks, List<Block> blockMap) {
        this.Blocks = blocks;
        this.Text = "";
        this.Lines = new ArrayList<>();
        this.Form = new Form();
        this.Tables = new ArrayList<>();
        this.Content = new ArrayList<>();

        for (Block b : blocks) {
            if (b.blockType().equals(BlockType.PAGE)) {
                this.Geometry = new NewGeometry(b.geometry());
                this.Id = b.id();
            } else if (b.blockType().equals(BlockType.LINE)) {
                Line l = new Line(b, blockMap);
                this.Lines.add(l);
                this.Content.add(l);
                this.Text += l.Text + System.lineSeparator();
            } else if (b.blockType().equals(BlockType.TABLE)) {
                Table t = new Table(b, blockMap);
                this.Tables.add(t);
                this.Content.add(t);
            } else if (b.blockType().equals(BlockType.KEY_VALUE_SET)) {
                if (b.entityTypes().contains(EntityType.KEY)) {
                    Field f = new Field(b, blockMap);
                    if (f.Key != null) {
                        this.Form.AddField(f);
                        this.Content.add(f);
                    }
                }
            }
        }
    }

    List<IndexedText> GetLinesInReadingOrder() {
        List<IndexedText> lines = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        for (Line line : this.Lines) {
            boolean columnFound = false;
            for (int index = 0; index < columns.size(); index++) {
                Column column = columns.get(index);
                BoundingBox bb = line.Geometry.boundingBox();
                float bbLeft = bb.left();
                float bbRight = bb.left() + bb.width();
                float bbCentre = bb.left() + (bb.width() / 2);
                float columnCentre = column.Left + (column.Right / 2);

                if ((bbCentre > column.Left && bbCentre < column.Right) || (columnCentre > bbLeft && columnCentre < bbRight)) {
                    lines.add(new IndexedText(index, line.Text));
                    columnFound = true;
                    break;
                }
            }
            if (!columnFound) {
                BoundingBox bb = line.Geometry.boundingBox();
                columns.add(new Column(bb.left(), bb.left() + bb.width()));
                lines.add(new IndexedText(columns.size() - 1, line.Text));
            }
        }
        lines.stream().filter(line -> line.ColumnIndex == 0).forEach(System.out::println);
        return lines;
    }

    String GetTextInReadingOrder() {
        List<IndexedText> lines = this.GetLinesInReadingOrder();
        StringBuilder text = new StringBuilder();
        for (IndexedText line : lines) {
            text.append(line.Text).append(System.lineSeparator());
        }
        return text.toString();
    }

    @Override
    public String toString() {
        List<String> result = new ArrayList<>();
        result.add(String.format("Page%n====%n"));
        for (Object c : this.Content) {
            result.add(String.format("%s%n", c));
        }
        return result.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    class Column {
        float Left;
        float Right;

        Column(float left, float right) {
            this.Left = left;
            this.Right = right;
        }

        @Override
        public String toString() {
            return String.format("Left: %f, Right: %f", this.Left, this.Right);
        }
    }

    class IndexedText {
        int ColumnIndex;
        String Text;

        IndexedText(int columnIndex, String text) {
            this.ColumnIndex = columnIndex;
            this.Text = text;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s", this.ColumnIndex, this.Text);
        }
    }
}

class NewGeometry {

    private BoundingBox boundingBox;
    private NewBoundingBox newBoundingBox;

    NewGeometry(Geometry geometry) {
        boundingBox = geometry.boundingBox();
        List<Point> polygon = geometry.polygon();
        NewBoundingBox bb = new NewBoundingBox(boundingBox.width(), boundingBox.height(), boundingBox.left(), boundingBox.top());
        List<Point> pgs = new ArrayList<>();
        for (Point pg : polygon) {
            pgs.add(Point.builder().x(pg.x()).y(pg.y()).build());
        }

        newBoundingBox = bb;
        polygon = pgs;
    }

    @Override
    public String toString() {
        return String.format("BoundingBox: %s%n", newBoundingBox);
    }
}

class NewBoundingBox {
    private float width;
    private float height;
    private float left;
    private float top;

    NewBoundingBox(float width, float height, float left, float top) {
        super();
        this.width = width;
        this.height = height;
        this.left = left;
        this.top = top;
    }

    @Override
    public String toString() {
        return String.format("width: %f, height: %f, left: %f, top: %f", width, height, left, top);
    }
}

class Line {
    float Confidence;
    Geometry Geometry;
    String Id;
    ArrayList<Word> Words;
    String Text;
    Block Block;

    Line(Block block, List<Block> blocks) {
        this.Block = block;
        this.Confidence = block.confidence();
        this.Geometry = block.geometry();
        this.Id = block.id();
        this.Text = block == null ? "" : block.text();
        this.Words = new ArrayList<>();

        List<Relationship> relationships = block.relationships();
        if (relationships != null && relationships.size() > 0) {
            for (Relationship r : relationships) {
                if (r.type().equals(RelationshipType.CHILD)) {
                    for (String id : r.ids()) {
                        Block wordBlock = blocks.stream().filter(b -> b.blockType().equals(BlockType.WORD) && b.id().equals(id)).findFirst().orElse(null);
                        if (wordBlock != null) {
                            Word w = new Word(wordBlock, blocks);
                            this.Words.add(w);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String toString() {

        return String.format(
                "Line%n====%n%s%nWords%n----%n%s%n----%n",
                this.Text,
                this.Words.stream().map(Object::toString).collect(Collectors.joining(", "))
        );
    }
}

class Form {
    List<Field> Fields;
    Map<String, Field> fieldMap;

    Form() {
        this.Fields = new ArrayList<>();
        this.fieldMap = new HashMap<>();
    }

    void AddField(Field field) {
        this.Fields.add(field);
        this.fieldMap.put(field.Key.toString(), field);
    }

    Field GetFieldByKey(String key) {
        return this.fieldMap.get(key);
    }

    List<Field> SearchFieldsByKey(String key) {
        List<Field> fields = new ArrayList<>();
        for (Field f : this.Fields) {
            if (f.Key.toString().toLowerCase().contains(key.toLowerCase())) {
                fields.add(f);
            }
        }
        return fields;
    }

    @Override
    public String toString() {
        return this.Fields.stream().map(Object::toString).collect(Collectors.joining(", "));
    }
}

class FieldValue {
    Block Block;
    float Confidence;
    Geometry Geometry;
    String Id;
    String Text;
    List<Object> Content;

    FieldValue(Block block, List<String> children, List<Block> blocks) {
        this.Block = block;
        this.Confidence = block.confidence();
        this.Geometry = block.geometry();
        this.Id = block.id();
        this.Text = "";
        this.Content = new ArrayList<>();

        List<String> words = new ArrayList<>();
        if (children != null && !children.isEmpty()) {
            for (String c : children) {
                Block wordBlock = blocks.stream().filter(b -> b.id().equals(c)).findFirst().orElse(null);
                if (wordBlock != null) {
                    if (wordBlock.blockType().equals(BlockType.WORD)) {
                        Word w = new Word(wordBlock, blocks);
                        this.Content.add(w);
                        words.add(w.Text);
                    } else if (wordBlock.blockType().equals(BlockType.SELECTION_ELEMENT)) {
                        SelectionElement selection = new SelectionElement(wordBlock, blocks);
                        this.Content.add(selection);
                        words.add(selection.SelectionStatus);
                    }
                }
            }
        }

        if (!words.isEmpty()) {
            this.Text = String.join(" ", words);
        }
    }

    @Override
    public String toString() {
        return Text;
    }
}

class FieldKey {
    Block Block;
    float Confidence;
    Geometry Geometry;
    String Id;
    String Text;
    List<Object> Content;

    FieldKey(Block block, List<String> children, List<Block> blocks) {
        this.Block = block;
        this.Confidence = block.confidence();
        this.Geometry = block.geometry();
        this.Id = block.id();
        this.Text = "";
        this.Content = new ArrayList<>();

        List<String> words = new ArrayList<>();

        if (children != null && !children.isEmpty()) {
            for (String c : children) {
                Block wordBlock = blocks.stream().filter(b -> b.id().equals(c)).findFirst().orElse(null);
                if (wordBlock != null && wordBlock.blockType().equals(BlockType.WORD)) {
                    Word w = new Word(wordBlock, blocks);
                    this.Content.add(w);
                    words.add(w.Text);
                }
            }
        }

        if (!words.isEmpty()) {
            this.Text = String.join(" ", words);
        }
    }

    @Override
    public String toString() {
        return Text;
    }
}

class Field {
    FieldKey Key;
    FieldValue Value;

    Field(Block block, List<Block> blocks) {
        List<Relationship> relationships = block.relationships();
        if (relationships != null && !relationships.isEmpty()) {
            for (Relationship r : relationships) {
                if (r.type().equals(RelationshipType.CHILD)) {
                    this.Key = new FieldKey(block, r.ids(), blocks);
                } else if (r.type().equals(RelationshipType.VALUE)) {
                    for (String id : r.ids()) {
                        Block v = blocks.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
                        if (v != null && v.entityTypes().contains(EntityType.VALUE)) {
                            List<Relationship> vr = v.relationships();
                            if (vr != null && !vr.isEmpty()) {
                                for (Relationship vc : vr) {
                                    if (vc.type().equals(RelationshipType.CHILD)) {
                                        this.Value = new FieldValue(v, vc.ids(), blocks);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        String k = this.Key == null ? "" : this.Key.toString();
        String v = this.Value == null ? "" : this.Value.toString();
        return String.format(
                "%nField%n====%nKey: %s, Value: %s%n",
                k,
                v
        );
    }
}

class Cell {
    int RowIndex;
    int RowSpan;
    int ColumnIndex;
    int ColumnSpan;
    List<Object> Content;
    Block Block;
    float Confidence;
    Geometry Geometry;
    String Id;
    String Text;

    Cell(Block block, List<Block> blocks) {
        this.Block = block;
        this.ColumnIndex = block.columnIndex();
        this.ColumnSpan = block.columnSpan();
        this.Confidence = block.confidence();
        this.Content = new ArrayList<>();
        this.Geometry = block.geometry();
        this.Id = block.id();
        this.RowIndex = block.rowIndex();
        this.RowSpan = block.rowSpan();
        this.Text = "";

        List<Relationship> relationships = block.relationships();
        if (relationships != null && !relationships.isEmpty()) {
            for (Relationship r : relationships) {
                if (r.type().equals(RelationshipType.CHILD)) {
                    for (String id : r.ids()) {
                        Block rb = blocks.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null);
                        if (rb != null) {
                            if (rb.blockType().equals(BlockType.WORD)) {
                                Word w = new Word(rb, blocks);
                                this.Content.add(w);
                                this.Text += w.Text + " ";
                            } else if (rb.blockType().equals(BlockType.SELECTION_ELEMENT)) {
                                SelectionElement se = new SelectionElement(rb, blocks);
                                this.Content.add(se);
                                this.Text += se.SelectionStatus + ", ";
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return this.Text;
    }
}


