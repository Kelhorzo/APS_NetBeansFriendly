import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

public class SortBenchApp extends JFrame {
    private static final int[] CAPACITIES = {1000, 10000, 100000, 500000, 1000000};

    private JPanel contentPanel;
    private JComboBox<String> algorithmBox;
    private JComboBox<String> capacityBox;
    private JLabel fileLabel;
    private JLabel statusLabel;
    private JButton uploadButton;
    private JButton runButton;
    private JTable resultsTable;
    private Path csvPath;

    private final DefaultTableModel tableModel = new DefaultTableModel(new String[]{"Algoritmo", "Capacidade", "Tempo (ms)", "Movimentações", "Comparações"}, 0);

    public SortBenchApp() {
        super("SortBench");
        initializeUI();
        setContentPane(contentPanel);
        setSize(850, 500);

        resultsTable.setModel(tableModel);
        resultsTable.setFillsViewportHeight(true);
        uploadButton.addActionListener(event -> chooseCsv());
        runButton.addActionListener(event -> runBenchmark());
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
        resultsTable = new JTable();
        statusLabel = new JLabel("Selecione um arquivo CSV para começar.");

        addComponent(uploadButton, 0, 0, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(fileLabel, 1, 0, 4, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(algorithmLabel, 0, 1, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(algorithmBox, 1, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(capacityLabel, 2, 1, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(capacityBox, 3, 1, 1, 1, 0, GridBagConstraints.HORIZONTAL);
        addComponent(runButton, 4, 1, 1, 0, 0, GridBagConstraints.NONE);
        addComponent(new JScrollPane(resultsTable), 0, 2, 5, 1, 1, GridBagConstraints.BOTH);
        addComponent(statusLabel, 0, 3, 5, 1, 0, GridBagConstraints.HORIZONTAL);
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
            statusLabel.setText("CSV selecionado. Cada linha deve conter um valor inteiro.");
        }
    }

    private void runBenchmark() {
        if (csvPath == null) {
            JOptionPane.showMessageDialog(this, "Selecione um arquivo CSV primeiro.", "Arquivo necessário", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String algorithm = (String) algorithmBox.getSelectedItem();
        int capacity = CAPACITIES[capacityBox.getSelectedIndex()];
        runButton.setEnabled(false);
        statusLabel.setText("Executando " + algorithm + "...");

        new SwingWorker<Result, Void>() {
            @Override protected Result doInBackground() throws Exception {
                int[] input = readCsv(csvPath);
                if (input.length < capacity) {
                    throw new IOException("O CSV contém " + input.length + " valores; são necessários " + capacity + ".");
                }
                int[] values = Arrays.copyOf(input, capacity);
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
                    tableModel.addRow(new Object[]{result.algorithm, formatCapacity(result.capacity),
                            String.format(java.util.Locale.US, "%.3f", result.elapsedNanos / 1000000.0),
                            result.movements, result.comparisons});
                    statusLabel.setText("Concluído: " + result.algorithm + " com " + formatCapacity(result.capacity) + " valores.");
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    statusLabel.setText("Não foi possível executar o benchmark.");
                    JOptionPane.showMessageDialog(SortBenchApp.this, cause.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static int[] readCsv(Path path) throws IOException {
        ArrayList<Integer> values = new ArrayList<>();
        int lineNumber = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.replace("\uFEFF", "").trim();
                if (line.isEmpty()) continue;

                Integer value = null;
                for (String cell : line.split("[,;\\t]", -1)) {
                    String text = cell.trim().replaceAll("^\"|\"$", "").trim();
                    try {
                        value = Integer.parseInt(text);
                        break;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (value == null) {
                    if (values.isEmpty()) continue;
                    throw new IOException("Linha " + lineNumber + " não contém um valor inteiro.");
                }
                values.add(value);
            }
        }
        if (values.isEmpty()) throw new IOException("O CSV não contém valores inteiros.");
        int[] result = new int[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    static void sort(int[] values, String algorithm, Counters counters) {
        switch (algorithm) {
            case "QuickSort" -> quickSort(values, 0, values.length - 1, counters);
            case "MergeSort" -> mergeSort(values, new int[values.length], 0, values.length - 1, counters);
            case "HeapSort" -> heapSort(values, counters);
            case "TimSort" -> timSort(values, counters);
            case "InsertionSort" -> insertionSort(values, 0, values.length, counters);
            default -> throw new IllegalArgumentException("Algoritmo desconhecido: " + algorithm);
        }
    }

    private static void quickSort(int[] a, int low, int high, Counters c) {
        if (low >= high) return;
        int i = low, j = high, pivot = a[low + (high - low) / 2];
        while (i <= j) {
            while (compare(a[i], pivot, c) < 0) i++;
            while (compare(a[j], pivot, c) > 0) j--;
            if (i <= j) {
                swap(a, i, j, c);
                i++;
                j--;
            }
        }
        if (low < j) quickSort(a, low, j, c);
        if (i < high) quickSort(a, i, high, c);
    }

    private static void mergeSort(int[] a, int[] temp, int low, int high, Counters c) {
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

    private static void heapSort(int[] a, Counters c) {
        for (int i = a.length / 2 - 1; i >= 0; i--) siftDown(a, i, a.length, c);
        for (int end = a.length - 1; end > 0; end--) {
            swap(a, 0, end, c);
            siftDown(a, 0, end, c);
        }
    }

    private static void siftDown(int[] a, int root, int size, Counters c) {
        while (root * 2 + 1 < size) {
            int child = root * 2 + 1;
            if (child + 1 < size && compare(a[child], a[child + 1], c) < 0) child++;
            if (compare(a[root], a[child], c) >= 0) return;
            swap(a, root, child, c);
            root = child;
        }
    }

    private static void insertionSort(int[] a, int start, int end, Counters c) {
        for (int i = start + 1; i < end; i++) {
            int value = a[i], j = i;
            while (j > start && compare(a[j - 1], value, c) > 0) {
                a[j] = a[j - 1];
                c.movements++;
                j--;
            }
            a[j] = value;
            c.movements++;
        }
    }

    private static void timSort(int[] a, Counters c) {
        int n = a.length;
        if (n < 2) return;
        int minRun = minRunLength(n);
        int[] bases = new int[n];
        int[] lengths = new int[n];
        int stackSize = 0;
        int[] temp = new int[n];

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

    private static int findRun(int[] a, int start, Counters c) {
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

    private static void mergeRuns(int[] a, int[] temp, int[] bases, int[] lengths, int index, Counters c) {
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

    private static int compare(int left, int right, Counters c) {
        c.comparisons++;
        return Integer.compare(left, right);
    }

    private static void swap(int[] a, int i, int j, Counters c) {
        if (i == j) return;
        int value = a[i];
        a[i] = a[j];
        a[j] = value;
        c.movements += 3;
    }

    private static String formatCapacity(int value) {
        return String.format(java.util.Locale.US, "%,d", value).replace(',', '.');
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

    public static void main(String[] args) {
        new SortBenchApp().setVisible(true);
    }
}
