package com.fangxuele.tool.push.ui.dialog;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fangxuele.tool.push.App;
import com.fangxuele.tool.push.dao.TPeopleDataMapper;
import com.fangxuele.tool.push.dao.TPeopleMapper;
import com.fangxuele.tool.push.dao.TTaskHisMapper;
import com.fangxuele.tool.push.dao.TTaskMapper;
import com.fangxuele.tool.push.domain.TPeople;
import com.fangxuele.tool.push.domain.TPeopleData;
import com.fangxuele.tool.push.domain.TTask;
import com.fangxuele.tool.push.domain.TTaskHis;
import com.fangxuele.tool.push.logic.TaskRunThread;
import com.fangxuele.tool.push.ui.UiConsts;
import com.fangxuele.tool.push.ui.form.MainWindow;
import com.fangxuele.tool.push.ui.form.PeopleEditForm;
import com.fangxuele.tool.push.ui.form.PeopleManageForm;
import com.fangxuele.tool.push.util.ComponentUtil;
import com.fangxuele.tool.push.util.MybatisUtil;
import com.fangxuele.tool.push.util.SqliteUtil;
import com.fangxuele.tool.push.util.SystemUtil;
import com.formdev.flatlaf.util.StringUtils;
import com.formdev.flatlaf.util.SystemInfo;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.opencsv.CSVReader;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TaskHisDetailDialog extends JDialog {
    private JPanel contentPane;
    private JTextArea textArea1;
    private JPanel pushUpPanel;
    private JLabel pushSuccessCount;
    private JLabel pushFailCount;
    private JLabel pushTotalProgressLabel;
    private JProgressBar pushTotalProgressBar;
    private JLabel pushLastTimeLabel;
    private JLabel jvmMemoryLabel;
    private JLabel availableProcessorLabel;
    private JLabel pushTotalCountLabel;
    private JLabel pushMsgName;
    private JLabel scheduleDetailLabel;
    private JLabel countPerThread;
    private JLabel pushLeftTimeLabel;
    private JLabel tpsLabel;
    private JButton pushStopButton;
    private JTextField successFileTextField;
    private JButton 打开Button;
    private JButton successToPeopleButton;
    private JTextField failFileTextField;
    private JButton failToPeopleButton;
    private JButton 打开Button1;
    private JTextField noSendFileTextField;
    private JButton 打开Button2;
    private JButton noSendToPeopleButton;

    private static TTaskHisMapper taskHisMapper = MybatisUtil.getSqlSession().getMapper(TTaskHisMapper.class);
    private static TTaskMapper taskMapper = MybatisUtil.getSqlSession().getMapper(TTaskMapper.class);
    private static TPeopleMapper peopleMapper = MybatisUtil.getSqlSession().getMapper(TPeopleMapper.class);
    private static TPeopleDataMapper peopleDataMapper = MybatisUtil.getSqlSession().getMapper(TPeopleDataMapper.class);

    private static final Log logger = LogFactory.get();

    private Boolean dialogClosed = false;

    public TaskHisDetailDialog() {
        super(App.mainFrame, "执行详情");
        ComponentUtil.setPreferSizeAndLocateToCenter(this, 0.5, 0.64);
        setContentPane(contentPane);
        setModal(true);

        if (SystemUtil.isMacOs() && SystemInfo.isMacFullWindowContentSupported) {
            this.getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
            this.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
            this.getRootPane().putClientProperty("apple.awt.fullscreenable", true);
            this.getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
            GridLayoutManager gridLayoutManager = (GridLayoutManager) contentPane.getLayout();
            gridLayoutManager.setMargin(new Insets(28, 0, 0, 0));
        }
        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onClose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    }

    private void onClose() {
        dialogClosed = true;
        dispose();
    }

    public TaskHisDetailDialog(TaskRunThread taskRunThread, Integer taskHisId) {
        this();

        successToPeopleButton.addActionListener(e -> {
            ThreadUtil.execute(() -> {
                PeopleEditForm peopleEditForm = PeopleEditForm.getInstance();
                JProgressBar memberTabImportProgressBar = peopleEditForm.getMemberTabImportProgressBar();
                CSVReader reader = null;
                try {
                    MainWindow.getInstance().getTabbedPane().setSelectedIndex(3);

                    TTaskHis tTaskHis = taskHisMapper.selectByPrimaryKey(taskHisId);
                    TTask tTask = taskMapper.selectByPrimaryKey(tTaskHis.getTaskId());

                    TPeople tPeopleToSave = new TPeople();
                    tPeopleToSave.setMsgType(tTask.getMsgType());
                    tPeopleToSave.setAccountId(tTask.getAccountId());
                    tPeopleToSave.setPeopleName(FileUtil.getName(tTaskHis.getSuccessFilePath()).replace(".csv", ""));
                    tPeopleToSave.setAppVersion(UiConsts.APP_VERSION);
                    String now = SqliteUtil.nowDateForSqlite();
                    tPeopleToSave.setCreateTime(now);
                    tPeopleToSave.setModifiedTime(now);

                    peopleMapper.insert(tPeopleToSave);

                    memberTabImportProgressBar.setVisible(true);
                    memberTabImportProgressBar.setIndeterminate(true);
                    File msgTemplateDataFile = new File(tTaskHis.getSuccessFilePath());
                    if (msgTemplateDataFile.exists()) {
                        // 可以解决中文乱码问题
                        DataInputStream in = new DataInputStream(new FileInputStream(msgTemplateDataFile));
                        reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        String[] nextLine;
                        TPeopleData tPeopleData;
                        while ((nextLine = reader.readNext()) != null) {
                            tPeopleData = new TPeopleData();
                            tPeopleData.setPeopleId(tPeopleToSave.getId());
                            tPeopleData.setPin(nextLine[0]);
                            tPeopleData.setVarData(JSONUtil.toJsonStr(nextLine));
                            tPeopleData.setAppVersion(UiConsts.APP_VERSION);
                            tPeopleData.setCreateTime(now);
                            tPeopleData.setModifiedTime(now);

                            peopleDataMapper.insert(tPeopleData);
                        }
                    }
                    PeopleManageForm.initPeopleList();

                    JOptionPane.showMessageDialog(App.mainFrame, "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e1) {
                    JOptionPane.showMessageDialog(App.mainFrame, "导入失败！\n\n" + e1.getMessage(), "失败",
                            JOptionPane.ERROR_MESSAGE);
                    logger.error(e1);
                } finally {
                    memberTabImportProgressBar.setMaximum(100);
                    memberTabImportProgressBar.setValue(100);
                    memberTabImportProgressBar.setIndeterminate(false);
                    memberTabImportProgressBar.setVisible(false);
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e1) {
                            logger.error(e1);
                            e1.printStackTrace();
                        }
                    }
                }

            });

            dispose();
        });

        failToPeopleButton.addActionListener(e -> {
            ThreadUtil.execute(() -> {
                PeopleEditForm peopleEditForm = PeopleEditForm.getInstance();
                JProgressBar memberTabImportProgressBar = peopleEditForm.getMemberTabImportProgressBar();
                CSVReader reader = null;
                try {
                    MainWindow.getInstance().getTabbedPane().setSelectedIndex(3);

                    TTaskHis tTaskHis = taskHisMapper.selectByPrimaryKey(taskHisId);
                    TTask tTask = taskMapper.selectByPrimaryKey(tTaskHis.getTaskId());

                    TPeople tPeopleToSave = new TPeople();
                    tPeopleToSave.setMsgType(tTask.getMsgType());
                    tPeopleToSave.setAccountId(tTask.getAccountId());
                    tPeopleToSave.setPeopleName(FileUtil.getName(tTaskHis.getFailFilePath()).replace(".csv", ""));
                    tPeopleToSave.setAppVersion(UiConsts.APP_VERSION);
                    String now = SqliteUtil.nowDateForSqlite();
                    tPeopleToSave.setCreateTime(now);
                    tPeopleToSave.setModifiedTime(now);

                    peopleMapper.insert(tPeopleToSave);

                    memberTabImportProgressBar.setVisible(true);
                    memberTabImportProgressBar.setIndeterminate(true);
                    File msgTemplateDataFile = new File(tTaskHis.getFailFilePath());
                    if (msgTemplateDataFile.exists()) {
                        // 可以解决中文乱码问题
                        DataInputStream in = new DataInputStream(new FileInputStream(msgTemplateDataFile));
                        reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        String[] nextLine;
                        TPeopleData tPeopleData;
                        while ((nextLine = reader.readNext()) != null) {
                            tPeopleData = new TPeopleData();
                            tPeopleData.setPeopleId(tPeopleToSave.getId());
                            tPeopleData.setPin(nextLine[0]);
                            tPeopleData.setVarData(JSONUtil.toJsonStr(nextLine));
                            tPeopleData.setAppVersion(UiConsts.APP_VERSION);
                            tPeopleData.setCreateTime(now);
                            tPeopleData.setModifiedTime(now);

                            peopleDataMapper.insert(tPeopleData);
                        }
                    }
                    PeopleManageForm.initPeopleList();

                    JOptionPane.showMessageDialog(App.mainFrame, "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e1) {
                    JOptionPane.showMessageDialog(App.mainFrame, "导入失败！\n\n" + e1.getMessage(), "失败",
                            JOptionPane.ERROR_MESSAGE);
                    logger.error(e1);
                } finally {
                    memberTabImportProgressBar.setMaximum(100);
                    memberTabImportProgressBar.setValue(100);
                    memberTabImportProgressBar.setIndeterminate(false);
                    memberTabImportProgressBar.setVisible(false);
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e1) {
                            logger.error(e1);
                            e1.printStackTrace();
                        }
                    }
                }

            });

            dispose();
        });

        noSendToPeopleButton.addActionListener(e -> {
            ThreadUtil.execute(() -> {
                PeopleEditForm peopleEditForm = PeopleEditForm.getInstance();
                JProgressBar memberTabImportProgressBar = peopleEditForm.getMemberTabImportProgressBar();
                CSVReader reader = null;
                try {
                    MainWindow.getInstance().getTabbedPane().setSelectedIndex(3);

                    TTaskHis tTaskHis = taskHisMapper.selectByPrimaryKey(taskHisId);
                    TTask tTask = taskMapper.selectByPrimaryKey(tTaskHis.getTaskId());

                    TPeople tPeopleToSave = new TPeople();
                    tPeopleToSave.setMsgType(tTask.getMsgType());
                    tPeopleToSave.setAccountId(tTask.getAccountId());
                    tPeopleToSave.setPeopleName(FileUtil.getName(tTaskHis.getNoSendFilePath()).replace(".csv", ""));
                    tPeopleToSave.setAppVersion(UiConsts.APP_VERSION);
                    String now = SqliteUtil.nowDateForSqlite();
                    tPeopleToSave.setCreateTime(now);
                    tPeopleToSave.setModifiedTime(now);

                    peopleMapper.insert(tPeopleToSave);

                    memberTabImportProgressBar.setVisible(true);
                    memberTabImportProgressBar.setIndeterminate(true);
                    File msgTemplateDataFile = new File(tTaskHis.getNoSendFilePath());
                    if (msgTemplateDataFile.exists()) {
                        // 可以解决中文乱码问题
                        DataInputStream in = new DataInputStream(new FileInputStream(msgTemplateDataFile));
                        reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        String[] nextLine;
                        TPeopleData tPeopleData;
                        while ((nextLine = reader.readNext()) != null) {
                            tPeopleData = new TPeopleData();
                            tPeopleData.setPeopleId(tPeopleToSave.getId());
                            tPeopleData.setPin(nextLine[0]);
                            tPeopleData.setVarData(JSONUtil.toJsonStr(nextLine));
                            tPeopleData.setAppVersion(UiConsts.APP_VERSION);
                            tPeopleData.setCreateTime(now);
                            tPeopleData.setModifiedTime(now);

                            peopleDataMapper.insert(tPeopleData);
                        }
                    }
                    PeopleManageForm.initPeopleList();

                    JOptionPane.showMessageDialog(App.mainFrame, "导入完成！", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e1) {
                    JOptionPane.showMessageDialog(App.mainFrame, "导入失败！\n\n" + e1.getMessage(), "失败",
                            JOptionPane.ERROR_MESSAGE);
                    logger.error(e1);
                } finally {
                    memberTabImportProgressBar.setMaximum(100);
                    memberTabImportProgressBar.setValue(100);
                    memberTabImportProgressBar.setIndeterminate(false);
                    memberTabImportProgressBar.setVisible(false);
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e1) {
                            logger.error(e1);
                            e1.printStackTrace();
                        }
                    }
                }

            });

            dispose();
        });

        BufferedReader logReader = null;

        if (taskRunThread != null) {
            try {
                logReader = new BufferedReader(new FileReader(taskRunThread.getLogFilePath()));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            BufferedReader finalLogReader = logReader;

            ThreadUtil.execAsync(() -> {
                while (true) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    try {
                        String line = finalLogReader.readLine();
                        if (line != null) {
                            textArea1.append(line + "\n");
                            textArea1.setCaretPosition(textArea1.getText().length());
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (!taskRunThread.running || dialogClosed) {
                        try {
                            finalLogReader.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                    pushSuccessCount.setText(String.valueOf(taskRunThread.getSuccessRecords()));
                    pushFailCount.setText(String.valueOf(taskRunThread.getFailRecords()));
                }
            });
        } else {
            TTaskHis tTaskHis = taskHisMapper.selectByPrimaryKey(taskHisId);

            pushSuccessCount.setText(String.valueOf(tTaskHis.getSuccessCnt()));
            pushFailCount.setText(String.valueOf(tTaskHis.getFailCnt()));

            successFileTextField.setText(tTaskHis.getSuccessFilePath());
            failFileTextField.setText(tTaskHis.getFailFilePath());
            noSendFileTextField.setText(tTaskHis.getNoSendFilePath());

            if (!StringUtils.isEmpty(tTaskHis.getLogFilePath())) {
                try {
                    logReader = new BufferedReader(new FileReader(tTaskHis.getLogFilePath()));

                    String line;
                    while ((line = logReader.readLine()) != null) {
                        textArea1.append(line + "\n");
                        textArea1.setCaretPosition(textArea1.getText().length());
                        if (dialogClosed) {
                            logReader.close();
                            break;
                        }
                    }
                    if (logReader != null) {
                        logReader.close();
                    }
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(1, 1, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 10, 0, 10), -1, -1));
        panel1.add(panel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pushUpPanel = new JPanel();
        pushUpPanel.setLayout(new GridLayoutManager(8, 10, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(pushUpPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        pushSuccessCount = new JLabel();
        Font pushSuccessCountFont = this.$$$getFont$$$(null, -1, 72, pushSuccessCount.getFont());
        if (pushSuccessCountFont != null) pushSuccessCount.setFont(pushSuccessCountFont);
        pushSuccessCount.setForeground(new Color(-13587376));
        pushSuccessCount.setText("0");
        pushUpPanel.add(pushSuccessCount, new GridConstraints(0, 0, 8, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pushFailCount = new JLabel();
        Font pushFailCountFont = this.$$$getFont$$$(null, -1, 72, pushFailCount.getFont());
        if (pushFailCountFont != null) pushFailCount.setFont(pushFailCountFont);
        pushFailCount.setForeground(new Color(-2200483));
        pushFailCount.setText("0");
        pushUpPanel.add(pushFailCount, new GridConstraints(0, 2, 8, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pushTotalProgressLabel = new JLabel();
        pushTotalProgressLabel.setText("总进度");
        pushUpPanel.add(pushTotalProgressLabel, new GridConstraints(7, 8, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pushTotalProgressBar = new JProgressBar();
        pushTotalProgressBar.setStringPainted(true);
        pushUpPanel.add(pushTotalProgressBar, new GridConstraints(7, 9, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("成功");
        pushUpPanel.add(label1, new GridConstraints(3, 1, 3, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("失败");
        pushUpPanel.add(label2, new GridConstraints(3, 3, 3, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JSeparator separator1 = new JSeparator();
        separator1.setOrientation(1);
        pushUpPanel.add(separator1, new GridConstraints(0, 4, 8, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        pushLastTimeLabel = new JLabel();
        pushLastTimeLabel.setEnabled(true);
        Font pushLastTimeLabelFont = this.$$$getFont$$$("Microsoft YaHei UI Light", -1, 36, pushLastTimeLabel.getFont());
        if (pushLastTimeLabelFont != null) pushLastTimeLabel.setFont(pushLastTimeLabelFont);
        pushLastTimeLabel.setForeground(new Color(-6710887));
        pushLastTimeLabel.setText("0s");
        pushUpPanel.add(pushLastTimeLabel, new GridConstraints(0, 6, 4, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setHorizontalAlignment(0);
        label3.setHorizontalTextPosition(0);
        label3.setText("耗时");
        pushUpPanel.add(label3, new GridConstraints(0, 5, 4, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JSeparator separator2 = new JSeparator();
        separator2.setOrientation(1);
        pushUpPanel.add(separator2, new GridConstraints(0, 7, 8, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        jvmMemoryLabel = new JLabel();
        jvmMemoryLabel.setText("JVM内存占用：--");
        pushUpPanel.add(jvmMemoryLabel, new GridConstraints(5, 8, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        availableProcessorLabel = new JLabel();
        availableProcessorLabel.setText("可用处理器核心：--");
        pushUpPanel.add(availableProcessorLabel, new GridConstraints(4, 8, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pushTotalCountLabel = new JLabel();
        pushTotalCountLabel.setText("消息总数：--");
        pushUpPanel.add(pushTotalCountLabel, new GridConstraints(1, 8, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pushMsgName = new JLabel();
        Font pushMsgNameFont = this.$$$getFont$$$(null, -1, 24, pushMsgName.getFont());
        if (pushMsgNameFont != null) pushMsgName.setFont(pushMsgNameFont);
        pushMsgName.setForeground(new Color(-276358));
        pushMsgName.setText("消息标题");
        pushUpPanel.add(pushMsgName, new GridConstraints(0, 8, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        scheduleDetailLabel = new JLabel();
        scheduleDetailLabel.setForeground(new Color(-276358));
        scheduleDetailLabel.setText("");
        pushUpPanel.add(scheduleDetailLabel, new GridConstraints(6, 8, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        countPerThread = new JLabel();
        countPerThread.setText("平均每个线程分配：--");
        pushUpPanel.add(countPerThread, new GridConstraints(3, 8, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("预计剩余");
        pushUpPanel.add(label4, new GridConstraints(4, 5, 3, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pushLeftTimeLabel = new JLabel();
        Font pushLeftTimeLabelFont = this.$$$getFont$$$("Microsoft YaHei UI Light", -1, 36, pushLeftTimeLabel.getFont());
        if (pushLeftTimeLabelFont != null) pushLeftTimeLabel.setFont(pushLeftTimeLabelFont);
        pushLeftTimeLabel.setForeground(new Color(-6710887));
        pushLeftTimeLabel.setText("0s");
        pushUpPanel.add(pushLeftTimeLabel, new GridConstraints(4, 6, 3, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("TPS");
        pushUpPanel.add(label5, new GridConstraints(7, 5, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        tpsLabel = new JLabel();
        tpsLabel.setText("0");
        pushUpPanel.add(tpsLabel, new GridConstraints(7, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("线程数：--");
        label6.setToolTipText("当前版本受http连接池限制建议不要设置过多线程，推荐100以内");
        pushUpPanel.add(label6, new GridConstraints(2, 8, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 10, 0, 10), -1, -1));
        panel1.add(panel3, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel3.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        textArea1 = new JTextArea();
        scrollPane1.setViewportView(textArea1);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel1.add(panel4, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("成功");
        panel4.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("失败");
        panel4.add(label8, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label9 = new JLabel();
        label9.setText("未发送");
        panel4.add(label9, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        successFileTextField = new JTextField();
        successFileTextField.setEditable(false);
        panel4.add(successFileTextField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        failFileTextField = new JTextField();
        failFileTextField.setEditable(false);
        panel4.add(failFileTextField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        noSendFileTextField = new JTextField();
        noSendFileTextField.setEditable(false);
        panel4.add(noSendFileTextField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        打开Button = new JButton();
        打开Button.setText("打开");
        panel4.add(打开Button, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        打开Button1 = new JButton();
        打开Button1.setText("打开");
        panel4.add(打开Button1, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        打开Button2 = new JButton();
        打开Button2.setText("打开");
        panel4.add(打开Button2, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        successToPeopleButton = new JButton();
        successToPeopleButton.setText("创建为人群");
        panel4.add(successToPeopleButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        failToPeopleButton = new JButton();
        failToPeopleButton.setText("创建为人群");
        panel4.add(failToPeopleButton, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        noSendToPeopleButton = new JButton();
        noSendToPeopleButton.setText("创建为人群");
        panel4.add(noSendToPeopleButton, new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pushStopButton = new JButton();
        pushStopButton.setEnabled(false);
        pushStopButton.setIcon(new ImageIcon(getClass().getResource("/icon/suspend.png")));
        pushStopButton.setText("停止");
        panel4.add(pushStopButton, new GridConstraints(3, 2, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}
