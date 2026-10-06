/*
 * Copyright (C) 2026 crono2250
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.openpnp.machine.reference.feeder.wizards;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.components.LocationButtonsPanel;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.MutableLocationProxy;
import org.openpnp.gui.support.PartsComboBoxModel;
import org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder;
import org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder.PartVisionMode;
import org.openpnp.model.Configuration;
import org.openpnp.model.Part;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;

/** Configuration panel appended to the standard ReferenceAutoFeeder wizard. */
public class ReferenceFiducialAutoFeederConfigurationWizard
        extends ReferenceAutoFeederConfigurationWizard {
    private final ReferenceFiducialAutoFeeder feeder;
    private final JTextField[] fidX = {new JTextField(8), new JTextField(8), new JTextField(8)};
    private final JTextField[] fidY = {new JTextField(8), new JTextField(8), new JTextField(8)};
    private final JTextField[] fidZ = {new JTextField(8), new JTextField(8), new JTextField(8)};
    private final JTextField[] fidRotation = {new JTextField(8), new JTextField(8), new JTextField(8)};
    private final JComboBox<Part> fidPart = new JComboBox<>();
    private PartsComboBoxModel partsModel;
    private final JTextField maxFidShift = new JTextField(6);
    private final JTextField maxBasisChange = new JTextField(6);
    private final JCheckBox periodic = new JCheckBox("Enable periodic calibration");
    private final JTextField interval = new JTextField(5);
    private final JCheckBox partVision = new JCheckBox("Recognize the fed part with the top camera");
    private final JComboBox<PartVisionMode> visionMode = new JComboBox<>(PartVisionMode.values());
    private final JTextField maxPartShift = new JTextField(6);
    private final JLabel status = new JLabel();

    public ReferenceFiducialAutoFeederConfigurationWizard(ReferenceFiducialAutoFeeder feeder) {
        super(feeder);
        this.feeder = feeder;

        JPanel marks = new JPanel(new GridBagLayout());
        marks.setBorder(new TitledBorder("Machine fiducials: nominal camera coordinates"));
        contentPanel.add(marks);
        addMarkCell(marks, new JLabel("Mark / position"), 0, 0);
        addMarkCell(marks, new JLabel("X"), 1, 0);
        addMarkCell(marks, new JLabel("Y"), 2, 0);
        addMarkCell(marks, new JLabel("Z"), 3, 0);
        addMarkCell(marks, new JLabel("Rotation"), 4, 0);
        addMarkCell(marks, new JLabel("Position controls"), 5, 0);
        String[] names = {"fid_A / front left", "fid_B / front right", "fid_C / rear right (origin)"};
        for (int i = 0; i < 3; i++) {
            addMarkCell(marks, new JLabel(names[i]), 0, i + 1);
            addMarkCell(marks, fidX[i], 1, i + 1);
            addMarkCell(marks, fidY[i], 2, i + 1);
            addMarkCell(marks, fidZ[i], 3, i + 1);
            addMarkCell(marks, fidRotation[i], 4, i + 1);
            addMarkCell(marks,
                    new LocationButtonsPanel(fidX[i], fidY[i], fidZ[i], fidRotation[i]), 5, i + 1);
        }

        JPanel calibration = new JPanel(new GridLayout(0, 2, 8, 5));
        calibration.setBorder(new TitledBorder("Three-point calibration"));
        contentPanel.add(calibration);
        partsModel = new PartsComboBoxModel();
        fidPart.setModel(partsModel);
        fidPart.setSelectedItem(null);
        fidPart.setRenderer(new IdentifiableListCellRenderer<Part>());
        calibration.add(new JLabel("Fiducial Part"));
        calibration.add(fidPart);
        calibration.add(new JLabel("Maximum mark shift (mm)"));
        calibration.add(maxFidShift);
        calibration.add(new JLabel("Maximum scale / shear change (%)"));
        calibration.add(maxBasisChange);
        calibration.add(periodic);
        calibration.add(new JLabel());
        calibration.add(new JLabel("Interval (minutes)"));
        calibration.add(interval);
        JButton calibrate = new JButton("Calibrate now");
        calibrate.addActionListener(e -> UiUtils.submitUiMachineTask(() -> {
            feeder.calibrateNow();
            SwingUtilities.invokeLater(this::updateStatus);
        }));
        calibration.add(calibrate);
        calibration.add(status);

        JPanel vision = new JPanel(new GridLayout(0, 2, 8, 5));
        vision.setBorder(new TitledBorder("Part vision"));
        contentPanel.add(vision);
        vision.add(partVision);
        vision.add(new JLabel());
        vision.add(new JLabel("Recognition mode"));
        vision.add(visionMode);
        vision.add(new JLabel("Maximum part shift (mm)"));
        vision.add(maxPartShift);
        JButton locate = new JButton("Locate part now");
        locate.addActionListener(e -> UiUtils.submitUiMachineTask(() -> feeder.locatePartNow()));
        vision.add(locate);
        vision.add(new JLabel());
        JButton edit = new JButton("Edit part pipeline");
        edit.addActionListener(e -> UiUtils.messageBoxOnException(() -> {
            feeder.getPartPipeline().setProperty("camera",
                    Configuration.get().getMachine().getDefaultHead().getDefaultCamera());
            feeder.getPartPipeline().setProperty("feeder", feeder);
            CvPipelineEditor editor = new CvPipelineEditor(feeder.getPartPipeline());
            JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                    feeder.getName() + " part pipeline", editor);
            dialog.setVisible(true);
        }));
        vision.add(edit);
        JButton reset = new JButton("Reset part pipeline");
        reset.addActionListener(e -> feeder.resetPartPipeline());
        vision.add(reset);
        updateStatus();
    }

    private static void addMarkCell(JPanel panel, Component component, int column, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.insets = new Insets(3, 4, 3, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = column == 5 ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        constraints.weightx = column == 0 ? 1.4 : column == 5 ? 0 : 1;
        panel.add(component, constraints);
    }

    private void updateStatus() {
        if (feeder.getLastCalibrationError() != null) {
            status.setText("Error: " + feeder.getLastCalibrationError());
        }
        else if (feeder.getLastCalibrationTime() == null) {
            status.setText("Not calibrated");
        }
        else {
            status.setText("Calibrated " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault()).format(feeder.getLastCalibrationTime()));
        }
    }

    @Override
    public void createBindings() {
        super.createBindings();
        LengthConverter length = new LengthConverter();
        DoubleConverter decimal = new DoubleConverter(Configuration.get().getLengthDisplayFormat());
        IntegerConverter integer = new IntegerConverter();
        MutableLocationProxy[] proxies = {new MutableLocationProxy(), new MutableLocationProxy(),
                new MutableLocationProxy()};
        String[] properties = {"fidALocation", "fidBLocation", "fidCLocation"};
        for (int i = 0; i < 3; i++) {
            bind(UpdateStrategy.READ_WRITE, feeder, properties[i], proxies[i], "location");
            addWrappedBinding(proxies[i], "lengthX", fidX[i], "text", length);
            addWrappedBinding(proxies[i], "lengthY", fidY[i], "text", length);
            addWrappedBinding(proxies[i], "lengthZ", fidZ[i], "text", length);
            addWrappedBinding(proxies[i], "rotation", fidRotation[i], "text", decimal);
            ComponentDecorators.decorateWithAutoSelectAndLengthConversion(fidX[i]);
            ComponentDecorators.decorateWithAutoSelectAndLengthConversion(fidY[i]);
            ComponentDecorators.decorateWithAutoSelectAndLengthConversion(fidZ[i]);
            ComponentDecorators.decorateWithAutoSelect(fidRotation[i]);
        }
        addWrappedBinding(feeder, "fiducialPart", fidPart, "selectedItem");
        addWrappedBinding(feeder, "maxFiducialShiftMm", maxFidShift, "text", decimal);
        addWrappedBinding(feeder, "maxBasisChangePercent", maxBasisChange, "text", decimal);
        addWrappedBinding(feeder, "periodicCalibration", periodic, "selected");
        addWrappedBinding(feeder, "calibrationIntervalMinutes", interval, "text", integer);
        addWrappedBinding(feeder, "partVisionEnabled", partVision, "selected");
        addWrappedBinding(feeder, "partVisionMode", visionMode, "selectedItem");
        addWrappedBinding(feeder, "maxPartShiftMm", maxPartShift, "text", decimal);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (partsModel != null) {
            partsModel.dispose();
        }
    }
}
