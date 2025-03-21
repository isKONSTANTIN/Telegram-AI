package su.knst.telegram.ai.utils;

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

public class DiagramGenerator {
    private static final String VERTEX_STYLE = "fillColor=#101218;fontColor=#f7f7f7;strokeColor=#dcdcdc;";
    private static final String EDGE_STYLE = "strokeColor=#aaaaaa;";
    private static final Color BACKGROUND = new Color(16, 18, 24);

    public static File generateDiagram(String[] nodes, int[][] edges, Type type) throws IOException {
        mxGraph graph = new mxGraph();

        Object parent = graph.getDefaultParent();

        graph.getModel().beginUpdate();
        try {
            Object[] vertices = new Object[nodes.length];
            for (int i = 0; i < nodes.length; i++) {
                int length = nodes[i].length();

                vertices[i] = graph.insertVertex(parent, null, nodes[i], 100, 100, Math.max(length * 7 + 20, 50), 30, VERTEX_STYLE);
            }

            for (int i = 0; i < edges.length; i++) {
                int[] edge = edges[i];

                for (int j : edge) {
                    if (j == -1 || j - 1 > vertices.length)
                        continue;

                    graph.insertEdge(parent, null, "", vertices[i], vertices[j - 1], EDGE_STYLE);
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
            case PARALLEL -> layout = new mxParallelEdgeLayout(graph);
            case PARTITION -> layout = new mxPartitionLayout(graph);
            case STACK -> layout = new mxStackLayout(graph);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }

        return layout;
    }

    public enum Type {
        HIERARCHICAL,
        ORGANIC,
        CIRCLE,
        PARALLEL,
        PARTITION,
        STACK
    }
}