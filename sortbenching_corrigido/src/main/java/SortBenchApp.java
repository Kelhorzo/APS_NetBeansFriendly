import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class SortBenchApp extends JFrame {
    private static final int[] CAPACITIES = {1000, 10000, 100000, 500000, 1000000};
    private static final String CSV_DATASET = "CSV";

    private JPanel contentPanel;
    private JComboBox<String> algorithmBox;
    private JComboBox<String> capacityBox;
    private JComboBox<String> datasetBox;
    private JPanel chartPanel;
    private JLabel fileLabel;
    private JLabel statusLabel;
    private JButton uploadButton;
    private JButton runButton;
    private JTable resultsTable;
    private Path csvPath;

    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Algoritmo", "Capacidade", "Tempo (ms)", "Movimentações", "Comparações"}, 0);
    private final List<Result> results = new ArrayList<>();
    private PerformanceChart performanceChart;

    public SortBenchApp() {
        super("SortBench");
        initializeUI();
        setContentPane(contentPanel);
        setSize(900, 700);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        resultsTable.setModel(tableModel);
        resultsTable.setFillsViewportHeight(true);
        performanceChart = new PerformanceChart();
        chartPanel.setLayout(new BorderLayout());
        chartPanel.add(performanceChart, BorderLayout.CENTER);
        uploadButton.addActionListener(event -> chooseCsv());
        runButton.addActionListener(event -> runBenchmark());
        datasetBox.addActionListener(event -> {
            boolean csvSelected = CSV_DATASET.equals(datasetBox.getSelectedItem());
            uploadButton.setEnabled(csvSelected);
            fileLabel.setEnabled(csvSelected);
        });
    }

    private void initializeUI() {
        contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        uploadButton = new JButton("Selecionar CSV");
        fileLabel = new JLabel("Nenhum CSV selecionado");
        JLabel algorithmLabel = new JLabel("Algoritmo:");
        algorithmBox = new JComboBox<>(new String[]{"QuickSort", "MergeSort", "HeapSort", "TimSort", "InsertionSort"});
        JLabel capacityLabel = new JLabel("Capacidade:");
        capacityBox = new JComboBox<>(new String[]{"1.000", "10.000", "100.000", "500.000", "1.000.000"});
        runButton = new JButton("Executar");
        JLabel datasetLabel = new JLabel("Conjunto:");
        datasetBox = new JComboBox<>(new String[]{"CSV", "Aleatório", "Crescente", "Decrescente", "Muitas duplicatas"});
        resultsTable = new JTable();
        chartPanel = new JPanel();
        statusLabel = new JLabel("Selecione um arquivo CSV para começar.");

        addComponent(uploadButton, 0, 0, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(fileLabel, 1, 0, 4, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(algorithmLabel, 0, 1, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(algorithmBox, 1, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(capacityLabel, 2, 1, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(capacityBox, 3, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(runButton, 4, 1, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(datasetLabel, 0, 2, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(datasetBox, 1, 2, 4, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(new JScrollPane(resultsTable), 0, 3, 5, 1, 1, GridBagConstraints.BOTH);
        addComponent(chartPanel, 0, 4, 5, 1, 1, GridBagConstraints.BOTH);
        addComponent(statusLabel, 0, 5, 5, 1, 0, GridBagConstraints.HORIZONTAL);
    }

    private void addComponent(Component component, int gridx, int gridy, int gridwidth,
                              double weightx, double weighty, int fill) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = gridx;
        constraints.gridy = gridy;
        constraints.gridwidth = gridwidth;
        constraints.weightx = weightx;
        constraints.weighty = weighty;
        constraints.fill = fill;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(4, 4, 4, 4);
        contentPanel.add(component, constraints);
    }

    private void chooseCsv() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            csvPath = chooser.getSelectedFile().toPath();
            fileLabel.setText(csvPath.getFileName().toString());
            statusLabel.setText("CSV selecionado. Valores decimais também são aceitos.");
            datasetBox.setSelectedItem(CSV_DATASET);
        }
    }

    private void runBenchmark() {
        String dataset = (String) datasetBox.getSelectedItem();
        if (CSV_DATASET.equals(dataset) && csvPath == null) {
            JOptionPane.showMessageDialog(this, "Selecione um arquivo CSV primeiro.", "Arquivo necessário", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String algorithm = (String) algorithmBox.getSelectedItem();
        int capacity = CAPACITIES[capacityBox.getSelectedIndex()];
        runButton.setEnabled(false);
        statusLabel.setText("Executando " + algorithm + "...");

        new SwingWorker<Result, Void>() {
            @Override protected Result doInBackground() throws Exception {
                BigDecimal[] input = CSV_DATASET.equals(dataset) ? readCsv(csvPath) : generateData(capacity, dataset);
                if (input.length < capacity) {
                    throw new IOException("O CSV contém " + input.length + " valores; são necessários " + capacity + ".");
                }
                BigDecimal[] values = Arrays.copyOf(input, capacity);
                Counters counters = new Counters();
                long start = System.nanoTime();
                sort(values, algorithm, counters);
                long elapsed = System.nanoTime() - start;
                return new Result(algorithm, capacity, elapsed, counters.movements, counters.comparisons);
            }

            @Override protected void done() {
                runButton.setEnabled(true);
                try {
                    Result result = get();
                    results.add(result);
                    tableModel.addRow(new Object[]{result.algorithm, result.capacity,
                            String.format(java.util.Locale.US, "%.3f", result.elapsedNanos / 1000000.0),
                            result.movements, result.comparisons});
                    performanceChart.repaint();
                    statusLabel.setText("Concluído: " + result.algorithm + " com " + result.capacity + " valores.");
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    statusLabel.setText("Não foi possível executar o benchmark.");
                    JOptionPane.showMessageDialog(SortBenchApp.this, cause.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    static BigDecimal[] generateData(int size, String pattern) {
        if (size < 0) throw new IllegalArgumentException("O tamanho não pode ser negativo.");
        BigDecimal[] values = new BigDecimal[size];
        Random random = new Random();
        switch (pattern) {
            case "Crescente" -> {
                for (int i = 0; i < size; i++) values[i] = BigDecimal.valueOf(i);
            }
            case "Decrescente" -> {
                for (int i = 0; i < size; i++) values[i] = BigDecimal.valueOf(size - i);
            }
            case "Muitas duplicatas" -> {
                for (int i = 0; i < size; i++) values[i] = BigDecimal.valueOf(random.nextInt(10));
            }
            case "Aleatório" -> {
                for (int i = 0; i < size; i++) values[i] = BigDecimal.valueOf(random.nextInt(-1_000_000, 1_000_001));
            }
            default -> throw new IllegalArgumentException("Tipo de conjunto desconhecido: " + pattern);
        }
        return values;
    }

    private static BigDecimal[] readCsv(Path path) throws IOException {
        ArrayList<BigDecimal> values = new ArrayList<>();
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.replace("\uFEFF", "").trim();
                if (line.isEmpty()) continue;

                boolean decimalCommaFile = line.indexOf(';') >= 0 || line.indexOf('\t') >= 0;
                String[] cells;
                if (!decimalCommaFile && line.matches("\"?[+-]?\\d+,\\d+\"?")) {
                    cells = new String[]{line};
                    decimalCommaFile = true;
                } else {
                    cells = line.split(decimalCommaFile ? "[;\\t]" : ",", -1);
                }
                BigDecimal value = null;
                for (String cell : cells) {
                    String text = cell.trim().replaceAll("^\"|\"$", "").trim();
                    if (decimalCommaFile) text = text.replace(',', '.');
                    try {
                        value = new BigDecimal(text);
                        break;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (value == null) {
                    if (values.isEmpty()) continue;
                    throw new IOException("Linha " + lineNumber + " não contém um valor numérico válido.");
                }
                values.add(value);
            }
        }
        if (values.isEmpty()) throw new IOException("O CSV não contém valores numéricos.");
        return values.toArray(BigDecimal[]::new);
    }

    static void sort(BigDecimal[] values, String algorithm, Counters counters) {
        switch (algorithm) {
            case "QuickSort" -> quickSort(values, 0, values.length - 1, counters);
            case "MergeSort" -> mergeSort(values, new BigDecimal[values.length], 0, values.length - 1, counters);
            case "HeapSort" -> heapSort(values, counters);
            case "TimSort" -> timSort(values, counters);
            case "InsertionSort" -> insertionSort(values, 0, values.length, counters);
            default -> throw new IllegalArgumentException("Algoritmo desconhecido: " + algorithm);
        }
    }

    private static void quickSort(BigDecimal[] a, int low, int high, Counters c) {
        if (low >= high) return;

        int firstSample = low + (high - low) / 3;
        int secondSample = low + 2 * (high - low) / 3;
        if (compare(a[firstSample], a[secondSample], c) > 0) swap(a, firstSample, secondSample, c);
        if (compare(a[firstSample], a[secondSample], c) == 0) {
            BigDecimal pivot = a[firstSample];
            int less = low, current = low, greater = high;
            while (current <= greater) {
                int order = compare(a[current], pivot, c);
                if (order < 0) swap(a, less++, current++, c);
                else if (order > 0) swap(a, current, greater--, c);
                else current++;
            }
            quickSort(a, low, less - 1, c);
            quickSort(a, greater + 1, high, c);
            return;
        }

        swap(a, low, firstSample, c);
        if (secondSample == low) secondSample = firstSample;
        swap(a, high, secondSample, c);
        BigDecimal pivot1 = a[low], pivot2 = a[high];
        int less = low + 1, great = high - 1;
        for (int k = less; k <= great; k++) {
            if (compare(a[k], pivot1, c) < 0) {
                swap(a, k, less++, c);
            } else if (compare(a[k], pivot2, c) > 0) {
                while (k < great && compare(a[great], pivot2, c) > 0) great--;
                swap(a, k, great--, c);
                if (compare(a[k], pivot1, c) < 0) swap(a, k, less++, c);
            }
        }
        int leftPivot = --less;
        int rightPivot = ++great;
        swap(a, low, leftPivot, c);
        swap(a, high, rightPivot, c);
        quickSort(a, low, leftPivot - 1, c);
        quickSort(a, leftPivot + 1, rightPivot - 1, c);
        quickSort(a, rightPivot + 1, high, c);
    }

    private static void mergeSort(BigDecimal[] a, BigDecimal[] temp, int low, int high, Counters c) {
        if (low >= high) return;
        int middle = low + (high - low) / 2;
        mergeSort(a, temp, low, middle, c);
        mergeSort(a, temp, middle + 1, high, c);
        int i = low, j = middle + 1, k = low;
        while (i <= middle && j <= high) temp[k++] = compare(a[i], a[j], c) <= 0 ? a[i++] : a[j++];
        while (i <= middle) temp[k++] = a[i++];
        while (j <= high) temp[k++] = a[j++];
        c.movements += high - low + 1L;
        System.arraycopy(temp, low, a, low, high - low + 1);
        c.movements += high - low + 1L;
    }

    private static void heapSort(BigDecimal[] a, Counters c) {
        for (int i = a.length / 2 - 1; i >= 0; i--) siftDown(a, i, a.length, c);
        for (int end = a.length - 1; end > 0; end--) {
            swap(a, 0, end, c);
            siftDown(a, 0, end, c);
        }
    }

    private static void siftDown(BigDecimal[] a, int root, int size, Counters c) {
        while (root * 2 + 1 < size) {
            int child = root * 2 + 1;
            if (child + 1 < size && compare(a[child], a[child + 1], c) < 0) child++;
            if (compare(a[root], a[child], c) >= 0) return;
            swap(a, root, child, c);
            root = child;
        }
    }

    private static void insertionSort(BigDecimal[] a, int start, int end, Counters c) {
        for (int i = start + 1; i < end; i++) {
            BigDecimal value = a[i];
            int j = i;
            while (j > start && compare(a[j - 1], value, c) > 0) {
                a[j] = a[j - 1];
                c.movements++;
                j--;
            }
            a[j] = value;
            c.movements++;
        }
    }

    private static void timSort(BigDecimal[] a, Counters c) {
        int n = a.length;
        if (n < 2) return;
        int minRun = minRunLength(n);
        int[] bases = new int[n];
        int[] lengths = new int[n];
        int stackSize = 0;
        BigDecimal[] temp = new BigDecimal[n];

        for (int start = 0; start < n;) {
            int end = findRun(a, start, c);
            int runLength = end - start;
            int forcedLength = Math.min(Math.max(minRun, runLength), n - start);
            if (runLength < forcedLength) {
                insertionSort(a, start, start + forcedLength, c);
                runLength = forcedLength;
            }
            bases[stackSize] = start;
            lengths[stackSize++] = runLength;
            start += runLength;

            while (stackSize > 1) {
                int i = stackSize - 2;
                if (i > 0 && lengths[i - 1] <= lengths[i] + lengths[i + 1]) {
                    if (lengths[i - 1] < lengths[i + 1]) i--;
                } else if (lengths[i] > lengths[i + 1]) {
                    break;
                }
                mergeRuns(a, temp, bases, lengths, i, c);
                stackSize--;
            }
        }
        while (stackSize > 1) {
            int i = stackSize - 2;
            if (i > 0 && lengths[i - 1] < lengths[i + 1]) i--;
            mergeRuns(a, temp, bases, lengths, i, c);
            stackSize--;
        }
    }

    private static int findRun(BigDecimal[] a, int start, Counters c) {
        int end = start + 1;
        if (end == a.length) return end;
        if (compare(a[end], a[start], c) < 0) {
            while (++end < a.length && compare(a[end], a[end - 1], c) < 0) { }
            for (int left = start, right = end - 1; left < right; left++, right--) swap(a, left, right, c);
        } else {
            while (++end < a.length && compare(a[end], a[end - 1], c) >= 0) { }
        }
        return end;
    }

    private static int minRunLength(int n) {
        int remainder = 0;
        while (n >= 64) {
            remainder |= n & 1;
            n >>= 1;
        }
        return n + remainder;
    }

    private static void mergeRuns(BigDecimal[] a, BigDecimal[] temp, int[] bases, int[] lengths, int index, Counters c) {
        int start = bases[index];
        int middle = start + lengths[index];
        int end = middle + lengths[index + 1];
        System.arraycopy(a, start, temp, start, end - start);
        c.movements += end - start;
        int left = start, right = middle;
        for (int out = start; out < end; out++) {
            if (left >= middle) a[out] = temp[right++];
            else if (right >= end) a[out] = temp[left++];
            else if (compare(temp[left], temp[right], c) <= 0) a[out] = temp[left++];
            else a[out] = temp[right++];
            c.movements++;
        }
        lengths[index] += lengths[index + 1];
        for (int i = index + 1; i < lengths.length - 1 && lengths[i] != 0; i++) {
            bases[i] = bases[i + 1];
            lengths[i] = lengths[i + 1];
        }
        lengths[lengths.length - 1] = 0;
    }

    private static int compare(BigDecimal left, BigDecimal right, Counters c) {
        c.comparisons++;
        return left.compareTo(right);
    }

    private static void swap(BigDecimal[] a, int i, int j, Counters c) {
        if (i == j) return;
        BigDecimal value = a[i];
        a[i] = a[j];
        a[j] = value;
        c.movements += 3;
    }



    static final class Counters {
        long comparisons;
        long movements;
    }

    private static final class Result {
        final String algorithm;
        final int capacity;
        final long elapsedNanos;
        final long movements;
        final long comparisons;
        Result(String algorithm, int capacity, long elapsedNanos, long movements, long comparisons) {
            this.algorithm = algorithm;
            this.capacity = capacity;
            this.elapsedNanos = elapsedNanos;
            this.movements = movements;
            this.comparisons = comparisons;
        }
    }

    private final class PerformanceChart extends JPanel {
        private final Color[] colors = {new Color(48, 112, 190), new Color(230, 126, 34), new Color(39, 156, 112), new Color(160, 90, 180), new Color(205, 70, 70)};

        PerformanceChart() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(760, 210));
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 150, right = 24, top = 32, rowHeight = 25;
            g.setColor(new Color(45, 50, 60));
            g.setFont(getFont().deriveFont(Font.BOLD, 14f));
            g.drawString("Comparativo de tempo (ms)", 12, 20);
            if (results.isEmpty()) {
                g.setFont(getFont().deriveFont(12f));
                g.drawString("Execute benchmarks para visualizar os resultados.", 16, 52);
                g.dispose();
                return;
            }
            double max = results.stream().mapToDouble(r -> r.elapsedNanos / 1_000_000.0).max().orElse(1.0);
            if (max <= 0) max = 1.0;
            int plotWidth = Math.max(20, getWidth() - left - right);
            for (int i = 0; i < results.size(); i++) {
                Result result = results.get(i);
                int y = top + i * rowHeight;
                double ms = result.elapsedNanos / 1_000_000.0;
                int width = (int) Math.round(plotWidth * ms / max);
                g.setColor(new Color(45, 50, 60));
                g.setFont(getFont().deriveFont(11f));
                g.drawString(result.algorithm + " · " + result.capacity, 10, y + 15);
                g.setColor(colors[i % colors.length]);
                g.fillRoundRect(left, y + 2, Math.max(2, width), 16, 7, 7);
                g.setColor(new Color(45, 50, 60));
                g.drawString(String.format(java.util.Locale.US, "%.3f ms", ms), left + Math.min(width + 7, plotWidth - 48), y + 15);
            }
            g.dispose();
        }
    }

    public static void main(String[] args) {
        new SortBenchApp().setVisible(true);
    }
}
