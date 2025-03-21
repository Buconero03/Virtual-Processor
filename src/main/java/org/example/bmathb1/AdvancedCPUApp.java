package org.example.bmathb1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.concurrent.Task;

import java.util.*;

/**
 * Esempio avanzato di un processore virtuale con:
 * - [CODE] / [DATA] segmenti
 * - Registri R0..R7, IP (instruction pointer), FLAGS
 * - Parser con commenti ';', label su righe a sé, GOTO, CALL SUB(2), SHIFT, MOD, eccezioni overflow
 * - Debug step-by-step
 */
public class AdvancedCPUApp extends Application {

    private TextArea codeArea;         // Dove l'utente scrive il codice
    private TextArea logArea;          // Log di esecuzione
    private ListView<String> memoryList;   // Visualizza data segment
    private Button parseButton;        // Parsare e caricare codice
    private Button runButton;          // Eseguire tutto
    private Button stepButton;         // Step by step

    private AdvancedCPU cpu;           // L'istanza "CPU virtuale"

    @Override
    public void start(Stage primaryStage) {
        codeArea = new TextArea();
        codeArea.setPrefRowCount(15);
        codeArea.setPrefColumnCount(40);
        codeArea.setText("""
                ; Esempio di codice con segmenti
                [CODE]
                START:
                  MOVI R0, 10
                  MOVI R1, 3
                  CALL SUB(2)
                  GOTO FINE   ; salta a label FINE

                SUB:
                  ; si aspetta 2 param: li poppa in R2, R3
                  POP R2
                  POP R3
                  MUL R2, R3
                  ; se overflow -> handle (esempio sempl.)
                  MOVR R0, R2
                  RET

                FINE:
                  HLT

                [DATA]
                X = 12
                Y = 99
                """);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);

        memoryList = new ListView<>();
        memoryList.setPrefSize(200, 300);

        parseButton = new Button("Parse/Load");
        parseButton.setOnAction(e -> doParseLoad());

        runButton = new Button("Run");
        runButton.setOnAction(e -> doRun());

        stepButton = new Button("Step");
        stepButton.setOnAction(e -> doStep());

        HBox topBox = new HBox(10, codeArea, memoryList);
        topBox.setPadding(new Insets(10));
        HBox btnBox = new HBox(10, parseButton, runButton, stepButton);
        btnBox.setPadding(new Insets(10));
        VBox root = new VBox(10, topBox, btnBox, new Label("Execution Log:"), logArea);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 1000, 700);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Advanced Virtual CPU with Segmenti, Step Debug, Flags, etc.");
        primaryStage.show();
    }

    /**
     * Legge il testo in codeArea, parse i segmenti [CODE] e [DATA],
     * crea un'istanza CPU e carica i dati in memoria.
     */
    private void doParseLoad() {
        logArea.clear();

        String codeText = codeArea.getText();
        if (codeText.isBlank()) {
            appendLog("Nessun codice presente!\n");
            return;
        }

        // Crea un'istanza CPU, parse, ecc.
        try {
            cpu = new AdvancedCPU(codeText, logArea);
            appendLog("Codice caricato con successo.\n");
            // Visualizza la data segment
            refreshMemoryView();
        } catch (Exception ex) {
            appendLog("Errore parse: " + ex.getMessage() + "\n");
            ex.printStackTrace();
        }
    }

    /**
     * Esegue l'intero programma in un Task separato, per non bloccare la UI.
     */
    private void doRun() {
        if (cpu == null) {
            appendLog("Prima fai Parse/Load!\n");
            return;
        }
        // Se il CPU è già in esecuzione e non è halted, evitiamo conflitti
        if (!cpu.isHalted() && cpu.isRunning()) {
            appendLog("CPU già in esecuzione.\n");
            return;
        }

        // Creiamo un task che esegue
        Task<Void> runTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                cpu.setRunning(true);  // abilita esecuzione
                while (!cpu.isHalted() && cpu.isRunning()) {
                    cpu.step();
                    // Aggiorna la UI (memoria, log, registri) se necessario
                    Platform.runLater(() -> refreshMemoryView());
                    Thread.sleep(200); // piccolo delay per vedere step
                }
                return null;
            }
        };
        runTask.setOnSucceeded(e -> {
            cpu.setRunning(false);
            appendLog("Esecuzione terminata.\n");
        });
        runTask.setOnFailed(e -> {
            cpu.setRunning(false);
            appendLog("Esecuzione terminata con errore.\n");
        });
        new Thread(runTask).start();
    }

    /**
     * Esegue un singolo step: decodifica l'istruzione corrente.
     */
    private void doStep() {
        if (cpu == null) {
            appendLog("Prima fai Parse/Load!\n");
            return;
        }
        if (cpu.isHalted()) {
            appendLog("CPU è già in HALT.\n");
            return;
        }
        // Esegui step
        cpu.setRunning(true); // momentaneamente
        cpu.step();
        cpu.setRunning(false);
        refreshMemoryView();
        appendLog("Step eseguito.\n");
    }

    /**
     * Aggiorna la ListView che mostra la data segment.
     */
    private void refreshMemoryView() {
        if (cpu == null) return;
        memoryList.getItems().clear();
        double[] dataSeg = cpu.getDataSegment();
        for (int i = 0; i < dataSeg.length; i++) {
            // Se preferisci vederli in esadecimale:
            // memoryList.getItems().add(String.format("[%02X] = %.4f", i, dataSeg[i]));
            memoryList.getItems().add(String.format("[%03d] = %.4f", i, dataSeg[i]));
        }
        // eventuale log di stato registri
        memoryList.getItems().add("----- REGISTRI -----");
        for (int i = 0; i < 8; i++) {
            memoryList.getItems().add(String.format("R%d = %.4f", i, cpu.getRegister(i)));
        }
        memoryList.getItems().add(String.format("IP = %d", cpu.getIP()));
        memoryList.getItems().add(String.format("FLAGS = 0x%X", cpu.getFLAGS()));
    }

    private void appendLog(String msg) {
        logArea.appendText(msg);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

/**
 * Classe "CPU" che gestisce:
 * - Segmenti [CODE] e [DATA]
 * - Registri R0..R7, IP, FLAGS
 * - Parser di istruzioni con commenti ';', label su riga a sé, GOTO, CALL SUB(2)
 * - Overflow, underflow, stack, subroutine con param
 * - Step-by-step
 */
class AdvancedCPU {
    public static final int DATA_SIZE = 256;

    // Registri
    private final double[] regs = new double[8];
    // Instruction Pointer
    private int IP = 0;
    // FLAGS (bit generici, es. 0=carry,1=zero,...)
    private int FLAGS = 0;

    // Stack pointer (usiamo la parte alta di dataSegment?)
    private int SP = DATA_SIZE - 1;

    // Segmenti
    private final List<String> codeSegment = new ArrayList<>();
    private final double[] dataSegment = new double[DATA_SIZE];

    // Label map
    private final Map<String, Integer> labelMap = new HashMap<>();

    // Running / halted
    private boolean running = false;
    private boolean halted = false;

    // Log
    private final TextArea logArea;

    // Soglia max passi per prevenire loop infiniti
    private static final int MAX_STEPS = 2000;
    private int stepCount = 0;

    public AdvancedCPU(String fullSource, TextArea logArea) throws Exception {
        this.logArea = logArea;
        parseSource(fullSource);
        setRunning(false);
        setHalted(false);
    }

    /**
     * Legge il testo e separa in segmenti [CODE] e [DATA].
     * Esegue un parsing di base:
     * - Rimuove i commenti con ';'
     * - Trova label (LABEL:) su righe a sé e crea una mappa label=>indirizzo
     * - Riempe codeSegment con le istruzioni
     * - Riempe dataSegment con i dati (X=10)
     */
    private void parseSource(String src) throws Exception {
        String[] lines = src.split("\\r?\\n");

        boolean inCode = false;
        boolean inData = false;
        int dataIndex = 0;

        for (String line : lines) {
            // Rimuovi commenti con ';'
            int idxComment = line.indexOf(';');
            if (idxComment >= 0) {
                line = line.substring(0, idxComment);
            }
            line = line.trim();
            if (line.isEmpty()) continue;

            // Controlla i segmenti
            if (line.equalsIgnoreCase("[CODE]")) {
                inCode = true;
                inData = false;
                continue;
            } else if (line.equalsIgnoreCase("[DATA]")) {
                inData = true;
                inCode = false;
                continue;
            }

            if (inCode) {
                // Se la riga è "LABEL:" su riga a sé, registra la label
                // Oppure "LABEL: istruzione"
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    String label = parts[0].trim().toUpperCase();
                    labelMap.put(label, codeSegment.size()); // l'indirizzo è la size attuale
                    // se c'è qualcosa dopo i due punti, lo consideriamo istruzione
                    String after = parts.length > 1 ? parts[1].trim() : "";
                    if (!after.isEmpty()) {
                        codeSegment.add(after);
                    } else {
                        codeSegment.add("NOP"); // riga di sola label
                    }
                } else {
                    // Istruzione pura
                    codeSegment.add(line);
                }
            } else if (inData) {
                // Aspettiamo linee come "X = 10" o "Y=123"
                String[] parts = line.split("=", 2);
                if (parts.length < 2) {
                    // Errore di sintassi
                    throw new Exception("Sintassi data non valida: " + line);
                }
                String var = parts[0].trim(); // ignorato in questa demo
                double val = Double.parseDouble(parts[1].trim());
                if (dataIndex < DATA_SIZE) {
                    dataSegment[dataIndex++] = val;
                } else {
                    throw new Exception("Segmento dati pieno!");
                }
            } else {
                // Se troviamo righe fuori da [CODE] e [DATA], le ignoriamo o errore
                // Per esempio potresti dare errore
                // Qui le ignoriamo con un log
                log("[CPU] Riga fuori segmenti: " + line);
            }
        }
        log(String.format("[CPU] Caricate %d istruzioni in codeSegment.\n", codeSegment.size()));
        log(String.format("[CPU] Caricati %d valori in dataSegment.\n", dataIndex));
    }

    /**
     * Esegue un singolo step (una istruzione).
     * Se la CPU è HALT o c'è un errore, non fa nulla.
     */
    public void step() {
        if (halted) {
            log("[CPU] Già in HALT.\n");
            return;
        }
        if (IP < 0 || IP >= codeSegment.size()) {
            log("[CPU] IP fuori range, esecuzione termina.\n");
            halted = true;
            return;
        }
        if (stepCount++ > MAX_STEPS) {
            log("[CPU] Troppe istruzioni eseguite (> " + MAX_STEPS + "). HALT.\n");
            halted = true;
            return;
        }

        String line = codeSegment.get(IP).trim();
        IP++; // default increment
        execInstruction(line);
    }

    /**
     * Decodifica/esegue l'istruzione 'line'.
     * Supporta GOTO, SHIFT, CALL SUB(2), MOD, overflow, ecc.
     */
    private void execInstruction(String line) {
        if (line.isEmpty()) {
            return;
        }
        // Esempio sintassi:  SHIFT R0 LEFT 2   /  CALL SUB(2)
        try {
            // Riconosci istruzione e argomenti
            // Esempio basico: tokenizziamo su virgole e spazi
            // Cerchiamo pattern per CALL SUB(2)
            if (line.toUpperCase().startsWith("CALL")) {
                // es. CALL SUB(2)
                line = line.substring(4).trim();
                // splitted -> "SUB(2)"
                int parOpen = line.indexOf('(');
                int parClose = line.indexOf(')');
                int paramCount = 0;
                String labelName = line;
                if (parOpen > 0 && parClose > parOpen) {
                    // labelName = substring(0, parOpen)
                    labelName = line.substring(0, parOpen).trim();
                    String paramStr = line.substring(parOpen + 1, parClose).trim();
                    if (!paramStr.isEmpty()) {
                        paramCount = Integer.parseInt(paramStr);
                    }
                }
                doCALL(labelName, paramCount);
                return;
            }

            String[] parts = line.split("[,\\s]+");
            // Esempio "MOVI R0 10" => [MOVI, R0, 10]
            String opcode = parts[0].toUpperCase();
            switch (opcode) {
                case "HLT":
                    log("[CPU] HLT\n");
                    halted = true;
                    return;
                case "NOP":
                    log("[CPU] NOP\n");
                    return;
                case "MOVI": {
                    // MOVI R0 10
                    int r = parseRegister(parts[1]);
                    double val = Double.parseDouble(parts[2]);
                    regs[r] = val;
                    checkOverflow(r);
                    logf("[CPU] MOVI => R%d=%.4f\n", r, regs[r]);
                }
                break;
                case "MOVR": {
                    // MOVR R0 R1
                    int rDest = parseRegister(parts[1]);
                    int rSrc = parseRegister(parts[2]);
                    regs[rDest] = regs[rSrc];
                    checkOverflow(rDest);
                    logf("[CPU] MOVR => R%d=R%d(%.4f)\n", rDest, rSrc, regs[rDest]);
                }
                break;
                case "ADD": {
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    regs[rD] += regs[rS];
                    checkOverflow(rD);
                    logf("[CPU] ADD => R%d=%.4f\n", rD, regs[rD]);
                }
                break;
                case "SUB": {
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    regs[rD] -= regs[rS];
                    checkOverflow(rD);
                    logf("[CPU] SUB => R%d=%.4f\n", rD, regs[rD]);
                }
                break;
                case "MUL": {
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    regs[rD] *= regs[rS];
                    checkOverflow(rD);
                    logf("[CPU] MUL => R%d=%.4f\n", rD, regs[rD]);
                }
                break;
                case "DIV": {
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    if (regs[rS] == 0) {
                        log("[CPU] DIV by zero => R" + rD + "=0\n");
                        regs[rD] = 0;
                    } else {
                        regs[rD] /= regs[rS];
                        checkOverflow(rD);
                    }
                    logf("[CPU] DIV => R%d=%.4f\n", rD, regs[rD]);
                }
                break;
                case "MOD": {
                    // MOD R0 R1 => R0 = (int)R0 % (int)R1
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    int iD = (int) regs[rD];
                    int iS = (int) regs[rS];
                    if (iS == 0) {
                        log("[CPU] MOD by zero => R" + rD + "=0\n");
                        regs[rD] = 0;
                    } else {
                        regs[rD] = iD % iS;
                    }
                    checkOverflow(rD);
                    logf("[CPU] MOD => R%d=%.4f\n", rD, regs[rD]);
                }
                break;
                case "SHIFT": {
                    // SHIFT R0 LEFT 2 / SHIFT R0 RIGHT 3 / SHIFT R0 ARITH 1
                    int rD = parseRegister(parts[1]);
                    String direction = parts[2].toUpperCase();
                    int count = Integer.parseInt(parts[3]);
                    int val = (int) regs[rD];
                    switch (direction) {
                        case "LEFT" -> val <<= count;
                        case "RIGHT" -> val >>>= count;  // logical
                        case "ARITH" -> val >>= count;   // arithmetic
                        default -> {
                            log("[CPU] SHIFT: direzione sconosciuta: " + direction + "\n");
                            halted = true;
                            return;
                        }
                    }
                    regs[rD] = val;
                    logf("[CPU] SHIFT => R%d=%d\n", rD, val);
                }
                break;
                case "AND": {
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    int val = ((int) regs[rD]) & ((int) regs[rS]);
                    regs[rD] = val;
                    logf("[CPU] AND => R%d=%d\n", rD, val);
                }
                break;
                case "OR": {
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    int val = ((int) regs[rD]) | ((int) regs[rS]);
                    regs[rD] = val;
                    logf("[CPU] OR => R%d=%d\n", rD, val);
                }
                break;
                case "XOR": {
                    int rD = parseRegister(parts[1]);
                    int rS = parseRegister(parts[2]);
                    int val = ((int) regs[rD]) ^ ((int) regs[rS]);
                    regs[rD] = val;
                    logf("[CPU] XOR => R%d=%d\n", rD, val);
                }
                break;
                case "JMP":
                case "GOTO": {
                    // GOTO LABEL
                    String label = parts[1].toUpperCase();
                    doJMP(label);
                }
                break;
                case "JMPZ": {
                    // JMPZ R0 LABEL
                    int r = parseRegister(parts[1]);
                    String label = parts[2].toUpperCase();
                    if (regs[r] == 0) {
                        doJMP(label);
                        logf("[CPU] JMPZ => saltato a %s\n", label);
                    } else {
                        log("[CPU] JMPZ => condizione falsa\n");
                    }
                }
                break;
                case "CALL": {
                    // in teoria gestito sopra, ma se line= "CALL SUB"
                    // potremmo gestire paramCount=0
                    String label = parts[1].toUpperCase();
                    doCALL(label, 0);
                }
                break;
                case "RET": {
                    doRET();
                }
                break;
                case "PUSH": {
                    int r = parseRegister(parts[1]);
                    push(regs[r]);
                    logf("[CPU] PUSH => sp=%d val=%.4f\n", SP, regs[r]);
                }
                break;
                case "POP": {
                    int r = parseRegister(parts[1]);
                    regs[r] = pop();
                    checkOverflow(r);
                    logf("[CPU] POP => R%d=%.4f\n", r, regs[r]);
                }
                break;
                case "STORE": {
                    // STORE R0, 10 => dataSegment[10] = R0
                    int r = parseRegister(parts[1]);
                    int addr = Integer.parseInt(parts[2]);
                    if (addr < 0 || addr >= DATA_SIZE) {
                        log("[CPU] STORE: indirizzo fuori range " + addr + "\n");
                        halted = true;
                        return;
                    }
                    dataSegment[addr] = regs[r];
                    logf("[CPU] STORE => data[%d]=%.4f\n", addr, regs[r]);
                }
                break;
                case "LOAD": {
                    // LOAD R1, 20 => R1= data[20]
                    int r = parseRegister(parts[1]);
                    int addr = Integer.parseInt(parts[2]);
                    if (addr < 0 || addr >= DATA_SIZE) {
                        log("[CPU] LOAD: indirizzo fuori range " + addr + "\n");
                        halted = true;
                        return;
                    }
                    regs[r] = dataSegment[addr];
                    checkOverflow(r);
                    logf("[CPU] LOAD => R%d=%.4f\n", r, regs[r]);
                }
                break;
                default:
                    log("[CPU] Istruzione sconosciuta: " + opcode + "\n");
                    halted = true;
                    break;
            }
        } catch (Exception ex) {
            log("[CPU] Errore execInstruction: " + ex.getMessage() + "\n");
            halted = true;
        }
    }

    private void doJMP(String label) {
        if (!labelMap.containsKey(label)) {
            log("[CPU] JMP: label sconosciuta " + label + "\n");
            halted = true;
            return;
        }
        IP = labelMap.get(label);
        logf("[CPU] JMP => IP=%d (%s)\n", IP, label);
    }

    private void doCALL(String label, int paramCount) {
        if (!labelMap.containsKey(label)) {
            log("[CPU] CALL: label sconosciuta " + label + "\n");
            halted = true;
            return;
        }
        // push IP
        push(IP);
        // push paramCount
        push(paramCount);
        IP = labelMap.get(label);
        logf("[CPU] CALL => IP=%d, paramCount=%d\n", IP, paramCount);
    }

    private void doRET() {
        // pop paramCount
        int paramCount = (int) pop();
        // pop paramCount valori (scartiamo)
        for (int i = 0; i < paramCount; i++) {
            pop();
        }
        // pop returnAddress
        IP = (int) pop();
        logf("[CPU] RET => IP=%d (paramCount=%d)\n", IP, paramCount);
    }

    private void push(double val) {
        if (SP < 0) {
            log("[CPU] Stack Overflow!\n");
            halted = true;
            return;
        }
        dataSegment[SP] = val;
        SP--;
    }

    private double pop() {
        if (SP >= DATA_SIZE - 1) {
            log("[CPU] Stack Underflow!\n");
            halted = true;
            return 0;
        }
        SP++;
        return dataSegment[SP];
    }

    /**
     * Controlla se regs[r] è NaN o Infinity. Se sì, setta regs[r]=0 e un flag di overflow.
     */
    private void checkOverflow(int r) {
        double val = regs[r];
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            logf("[CPU] Overflow/NaN su R%d => set a 0\n", r);
            regs[r] = 0;
            // settiamo un bit di FLAGS, es. bit 0x01
            FLAGS |= 0x01;
        }
    }

    /**
     * Log in textArea.
     */
    private void log(String msg) {
        if (logArea != null) {
            Platform.runLater(() -> logArea.appendText(msg));
        }
    }

    private void logf(String fmt, Object... args) {
        log(String.format(fmt, args));
    }

    // GETTER e SETTER vari
    public double[] getDataSegment() {
        return dataSegment;
    }

    public double getRegister(int i) {
        return regs[i];
    }

    public int getIP() {
        return IP;
    }

    public int getFLAGS() {
        return FLAGS;
    }

    public boolean isHalted() {
        return halted;
    }

    public void setHalted(boolean halted) {
        this.halted = halted;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean run) {
        this.running = run;
    }

    private int parseRegister(String token) {
        // Esempio: "R3," -> r=3
        token = token.trim().toUpperCase();
        if (!token.startsWith("R")) {
            throw new IllegalArgumentException("Registro non valido: " + token);
        }
        String sub = token.substring(1);
        if (sub.endsWith(",")) {
            sub = sub.substring(0, sub.length() - 1);
        }
        int r = Integer.parseInt(sub);
        if (r < 0 || r >= regs.length) {
            throw new IllegalArgumentException("Registro fuori range: R" + r);
        }
        return r;
    }
}
