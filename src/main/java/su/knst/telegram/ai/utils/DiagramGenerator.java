package su.knst.telegram.ai.utils;

import app.finwave.tat.utils.Pair;
import com.mxgraph.layout.*;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.view.mxGraph;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class DiagramGenerator {
    private static final String VERTEX_STYLE = "fillColor=#13151c;fontColor=#f7f7f7;strokeColor=#dcdcdc;";
    private static final String EDGE_STYLE = "strokeColor=#aaaaaa;";
    private static final Color BACKGROUND = new Color(16, 18, 24);

    public static File generateDiagram(List<Block> blocks, Type type) throws IOException {
        mxGraph graph = new mxGraph();

        Object parent = graph.getDefaultParent();

        graph.getModel().beginUpdate();
        try {
            HashMap<Integer, Pair<Block, Object>> blockMap = new HashMap<>();

            for (Block block : blocks) {
                int length = block.title.length();

                Object vertex = graph.insertVertex(parent, null, block, 100, 100, Math.max(length * 7 + 20, 50), 30, VERTEX_STYLE);
                blockMap.put(block.id, Pair.of(block, vertex));
            }

            for (Block block : blocks) {
                List<Integer> ct = block.connectingTo;

                if (ct == null)
                    continue;

                Object sourceVertex = blockMap.get(block.id).second();

                for (int blockId : ct) {
                    Object targetVertex = blockMap.get(blockId).second();

                    graph.insertEdge(parent, null, "", sourceVertex, targetVertex, EDGE_STYLE);
                }
            }

            mxGraphLayout layout = getMxGraphLayout(type, graph);
            layout.execute(parent);
        } finally {
            graph.getModel().endUpdate();
        }

        BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, 1.5, BACKGROUND, true, null);

        File imgFile = File.createTempFile("knst_ai_mx_graph_", ".tmp");
        ImageIO.write(image, "PNG", imgFile);

        return imgFile;
    }

    private static mxGraphLayout getMxGraphLayout(Type type, mxGraph graph) {
        mxGraphLayout layout;

        switch (type) {
            case HIERARCHICAL -> layout = new mxHierarchicalLayout(graph);
            case ORGANIC -> layout = new mxOrganicLayout(graph);
            case CIRCLE -> layout = new mxCircleLayout(graph);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }

        return layout;
    }

    public enum Type {
        HIERARCHICAL,
        ORGANIC,
        CIRCLE
    }

    public static class Block {
        public int id;
        public String title;
        public List<Integer> connectingTo;

        public Block(int id, String title, List<Integer> connectingTo) {
            this.id = id;
            this.title = title;
            this.connectingTo = connectingTo;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}