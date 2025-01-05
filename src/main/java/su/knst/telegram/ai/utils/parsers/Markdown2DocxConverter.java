package su.knst.telegram.ai.utils.parsers;

import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.gfm.tables.internal.TableBlockParser;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class Markdown2DocxConverter {
    protected XWPFDocument document = new XWPFDocument();

    protected Markdown2DocxConverter() {
    }

    public static Optional<File> convert(String markdown) {
        Markdown2DocxConverter converter = new Markdown2DocxConverter();
        converter.parseNode(
                Parser.builder()
                        .extensions(Collections.singleton(TablesExtension.create()))
                        .build()
                .parse(markdown)
        );

        try {
            File outputFile = Files.createTempFile(Path.of("/","tmp","/"), "knst_ai_markdown2docx_",".docx").toFile();
            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                converter.document.write(out);
            }

            return Optional.of(outputFile);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    protected void parseNode(Node node) {
        Node current = node.getFirstChild();
        while (current != null) {
            if (current instanceof Heading heading) {
                addHeading(heading);
            } else if (current instanceof Paragraph paragraph) {
                addParagraph(paragraph);
            } else if (current instanceof FencedCodeBlock codeBlock) {
                addFencedCodeBlock(codeBlock);
            } else if (current instanceof BulletList list) {
                addList(list, 1);
            } else if (current instanceof OrderedList list) {
                addList(list, 1);
            } else if (current instanceof ThematicBreak thematicBreak) {
                addBreak(thematicBreak);
            } else if (current instanceof BlockQuote blockQuote) {
                addBlockQuote(blockQuote);
            } else if (current instanceof IndentedCodeBlock codeBlock) {
                addIndentedCodeBlock(codeBlock);
            } else if (current instanceof TableBlock table) {
                addTable(table);
            } else {
                System.out.println("Unknown node: " + current.getClass().getName());
                parseNode(current);
            }

            current = current.getNext();
        }
    }

    protected XWPFHyperlinkRun createHyperlinkRun(XWPFParagraph paragraph, String uri) {
        String rId = paragraph.getPart().getPackagePart().addExternalRelationship(
                uri,
                XWPFRelation.HYPERLINK.getRelation()
        ).getId();

        CTHyperlink cthyperLink=paragraph.getCTP().addNewHyperlink();
        cthyperLink.setId(rId);
        cthyperLink.addNewR();

        return new XWPFHyperlinkRun(
                cthyperLink,
                cthyperLink.getRArray(0),
                paragraph
        );
    }

    protected void appendToRun(XWPFRun run, Node node) {
        if (node instanceof Text text) {
            run.setText(text.getLiteral());
            return;
        }

        if (node instanceof Code code) {
            run.setFontFamily("FreeMono");
            run.setText(code.getLiteral());
            return;
        }

        if (node instanceof Emphasis emphasis) {
            run.setItalic(true);

            appendToRun(run, emphasis.getFirstChild());
            return;
        }

        if (node instanceof StrongEmphasis emphasis) {
            run.setBold(true);

            appendToRun(run, emphasis.getFirstChild());

            return;
        }

        if (node instanceof Link link) {
            XWPFHyperlinkRun hyperlink = createHyperlinkRun(run.getParagraph(), link.getDestination());
            String title = link.getTitle();

            if (title == null && link.getFirstChild() instanceof Text text) {
                title = text.getLiteral();
            } else if (title == null) {
                title = link.getDestination();
            }

            hyperlink.setText(title);
            hyperlink.setColor("0000FF");
            hyperlink.setUnderline(UnderlinePatterns.SINGLE);

            return;
        }
    }

    protected void appendRow(XWPFTableRow row, TableRow tableRow) {
        TableCell tableCell = (TableCell) tableRow.getFirstChild();

        int index = 0;
        int createdCells = row.getTableCells().size();

        while (tableCell != null) {
            XWPFTableCell cell = index < createdCells ? row.getCell(index) : row.createCell();
            XWPFParagraph paragraph = cell.addParagraph();

            if (tableCell.getAlignment() != null)
                paragraph.setAlignment(
                        switch (tableCell.getAlignment()) {
                            case LEFT -> ParagraphAlignment.LEFT;
                            case CENTER -> ParagraphAlignment.CENTER;
                            case RIGHT -> ParagraphAlignment.RIGHT;
                        }
                );

            XWPFRun run = paragraph.createRun();

            for (Node node = tableCell.getFirstChild(); node != null; node = node.getNext())
                appendToRun(run, node);

            tableCell = (TableCell) tableCell.getNext();
            index++;
        }
    }

    protected void addTable(TableBlock table) {
        XWPFTable xwpfTable = document.createTable();

        ArrayList<TableRow> rows = new ArrayList<>();

        if (table.getFirstChild() instanceof TableHead head) {
            Node row = head.getFirstChild();

            while (row != null) {
                if (row instanceof TableRow tableRow)
                    rows.add(tableRow);

                row = row.getNext();
            }
        }

        if (table.getLastChild() instanceof TableBody body) {
            Node row = body.getFirstChild();

            while (row != null) {
                if (row instanceof TableRow tableRow)
                    rows.add(tableRow);

                row = row.getNext();
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            TableRow tableRow = rows.get(i);
            appendRow(i == 0 ? xwpfTable.getRow(i) : xwpfTable.createRow(), tableRow);
        }
    }

    protected void addBlockQuote(BlockQuote blockQuote) {
        for (Node child = blockQuote.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Paragraph paragraphNode) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setBorderLeft(Borders.SINGLE);

                for (Node node = paragraphNode.getFirstChild(); node != null; node = node.getNext()) {
                    XWPFRun run = paragraph.createRun();
                    appendToRun(run, node);
                }
            } else if (child instanceof BlockQuote b) {
                addBlockQuote(b);
            }
        }
    }

    protected void addBreak(ThematicBreak thematicBreak) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setBorderBottom(Borders.SINGLE);
        document.createParagraph();
    }

    protected void addHeading(Heading heading) {
        XWPFParagraph paragraph = document.createParagraph();
        int lvl = heading.getLevel();

        paragraph.setSpacingBefore(32 * Math.max(7 - lvl, 0));
        paragraph.setSpacingAfter(32 * Math.max(7 - lvl, 0));

        for (Node node = heading.getFirstChild(); node != null; node = node.getNext()) {
            XWPFRun run = paragraph.createRun();
            run.setBold(true);
            run.setFontSize(26 - 2 * lvl);
            appendToRun(run, node);
        }
    }

    protected void addParagraph(Paragraph paragraphNode) {
        XWPFParagraph paragraph = document.createParagraph();

        for (Node node = paragraphNode.getFirstChild(); node != null; node = node.getNext()) {
            XWPFRun run = paragraph.createRun();
            appendToRun(run, node);
        }
    }

    protected void addIndentedCodeBlock(IndentedCodeBlock codeBlock) {
        String[] lines = codeBlock.getLiteral().split("\n");

        for (String line : lines) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("FreeMono");
            run.setText(line);
        }
    }

    protected void addFencedCodeBlock(FencedCodeBlock codeBlock) {
        String[] lines = codeBlock.getLiteral().split("\n");

        if (codeBlock.getInfo() != null) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setFontSize(10);
            run.setText(codeBlock.getInfo());
        }

        for (String line : lines) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setBorderBottom(Borders.SINGLE);
            paragraph.setBorderTop(Borders.SINGLE);
            paragraph.setBorderLeft(Borders.SINGLE);
            paragraph.setBorderRight(Borders.SINGLE);
            XWPFRun run = paragraph.createRun();
            run.setFontFamily("FreeMono");
            run.setText(line);
        }
    }

    protected void addList(ListBlock listBlock, int indent) {
        Node item = listBlock.getFirstChild();
        boolean isOrdered = listBlock instanceof OrderedList;
        String delimiter = !isOrdered ? "• " : ". ";

        int index = 0;

        if (isOrdered)
            index = Optional.ofNullable(((OrderedList) listBlock).getMarkerStartNumber()).orElse(1) - 1;

        while (item != null) {
            if (!(item instanceof ListItem listItem) || listItem.getFirstChild() == null) {
                item = item.getNext();

                continue;
            }

            for (Node content = listItem.getFirstChild(); content != null; content = content.getNext()) {
                if (content instanceof Paragraph paragraphContent) {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setIndentFromLeft(indent * 128);
                    XWPFRun run = paragraph.createRun();
                    run.setText(!isOrdered ? delimiter : (index + 1) + delimiter);

                    for (Node node = paragraphContent.getFirstChild(); node != null; node = node.getNext()) {
                        appendToRun(paragraph.createRun(), node);
                    }
                }else if (content instanceof ListBlock) {
                    addList((ListBlock) content, indent + 1);
                }
            }

            item = item.getNext();
            index++;
        }
    }
}
