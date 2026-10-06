/*
 * Copyright (C) 2026 crono2250
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.openpnp.machine.reference.feeder.wizards;

import java.awt.Component;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.LocationButtonsPanel;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.PartsComboBoxModel;
import org.openpnp.machine.reference.feeder.FiducialCatalog;
import org.openpnp.machine.reference.feeder.FiducialCatalog.Mark;
import org.openpnp.machine.reference.feeder.FiducialCatalog.Surface;
import org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder;
import org.openpnp.machine.reference.feeder.ReferenceFiducialAutoFeeder.PartVisionMode;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;

/** Calibration controls shown beside the standard ReferenceAutoFeeder Configuration tab. */
public class ReferenceFiducialAutoFeederCalibrationWizard extends AbstractConfigurationWizard {
    private final ReferenceFiducialAutoFeeder feeder;
    private final FiducialCatalog catalog;
    private final Runnable catalogListener;
    private final JComboBox<Surface> surfaceBox = new JComboBox<>();
    @SuppressWarnings("unchecked")
    private final JComboBox<Mark>[] fidSelection = new JComboBox[] {
            new JComboBox<Mark>(), new JComboBox<Mark>(), new JComboBox<Mark>()};
    private final JTextField[] fidX = {new JTextField(8), new JTextField(8), new JTextField(8)};
    private final JTextField[] fidY = {new JTextField(8), new JTextField(8), new JTextField(8)};
    private final JPanel fidCXCards = new JPanel(new CardLayout());
    private final JPanel fidCYCards = new JPanel(new CardLayout());
    private final FiducialXYControls[] fidControls = new FiducialXYControls[3];
    private final JButton[] saveMark = new JButton[3];
    private final JButton[] updateMark = new JButton[3];
    private final JButton[] deleteMark = new JButton[3];
    private final JCheckBox useMachineOrigin = new JCheckBox("Use machine origin (X=0, Y=0)");
    private boolean refreshing;
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

    public ReferenceFiducialAutoFeederCalibrationWizard(ReferenceFiducialAutoFeeder feeder) {
        super();
        this.feeder = feeder;
        this.catalog = FiducialCatalog.get();
        feeder.getOrCreateSurface();

        JPanel surfaces = new JPanel(new GridBagLayout());
        surfaces.setBorder(new TitledBorder("Installation surface"));
        contentPanel.add(surfaces);
        addMarkCell(surfaces, new JLabel("Surface"), 0, 0);
        addMarkCell(surfaces, surfaceBox, 1, 0);
        JButton newSurface = new JButton("New surface");
        newSurface.addActionListener(e -> UiUtils.messageBoxOnException(this::createSurface));
        addMarkCell(surfaces, newSurface, 2, 0);
        JButton renameSurface = new JButton("Rename");
        renameSurface.addActionListener(e -> UiUtils.messageBoxOnException(this::renameSurface));
        addMarkCell(surfaces, renameSurface, 3, 0);
        JButton deleteSurface = new JButton("Delete surface");
        deleteSurface.addActionListener(e -> UiUtils.messageBoxOnException(this::deleteSurface));
        addMarkCell(surfaces, deleteSurface, 4, 0);
        surfaceBox.addActionListener(e -> {
            if (refreshing) {
                return;
            }
            Surface selected = (Surface) surfaceBox.getSelectedItem();
            feeder.setFiducialSurfaceId(selected == null ? null : selected.getId());
            refreshCatalogUI();
        });

        JPanel marks = new JPanel(new GridBagLayout());
        marks.setBorder(new TitledBorder("Shared machine fiducials: nominal camera coordinates"));
        contentPanel.add(marks);
        addMarkCell(marks, new JLabel("Mark"), 0, 0);
        addMarkCell(marks, new JLabel("Saved fiducial"), 1, 0);
        addMarkCell(marks, new JLabel("X"), 2, 0);
        addMarkCell(marks, new JLabel("Y"), 3, 0);
        addMarkCell(marks, new JLabel("Camera controls"), 4, 0);
        addMarkCell(marks, new JLabel("Manage selected fiducial"), 5, 0);
        addMarkCell(marks, new JLabel("Machine origin"), 6, 0);
        String[] names = {"fid_A / front left", "fid_B / front right", "fid_C / rear right"};
        for (int i = 0; i < 3; i++) {
            fidX[i].setEditable(false);
            fidY[i].setEditable(false);
            fidSelection[i].setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(javax.swing.JList<?> list,
                        Object value, int index, boolean selected, boolean focus) {
                    super.getListCellRendererComponent(list, value, index, selected, focus);
                    setText(value == null ? "Select fiducial" : ((Mark) value).getName());
                    return this;
                }
            });
        }
        fidCXCards.add(fidX[2], "custom");
        fidCXCards.add(disabledOriginField(), "origin");
        fidCYCards.add(fidY[2], "custom");
        fidCYCards.add(disabledOriginField(), "origin");
        for (int i = 0; i < 3; i++) {
            final int index = i;
            addMarkCell(marks, new JLabel(names[i]), 0, i + 1);
            addMarkCell(marks, fidSelection[i], 1, i + 1);
            addMarkCell(marks, i == 2 ? fidCXCards : fidX[i], 2, i + 1);
            addMarkCell(marks, i == 2 ? fidCYCards : fidY[i], 3, i + 1);
            fidControls[i] = new FiducialXYControls(fidX[i], fidY[i]);
            addMarkCell(marks, fidControls[i], 4, i + 1);
            JPanel manage = new JPanel();
            saveMark[i] = new JButton("Save new");
            saveMark[i].addActionListener(e -> UiUtils.messageBoxOnException(() -> saveNewMark(index)));
            manage.add(saveMark[i]);
            updateMark[i] = new JButton("Update");
            updateMark[i].addActionListener(e -> UiUtils.messageBoxOnException(() -> updateMark(index)));
            manage.add(updateMark[i]);
            deleteMark[i] = new JButton("Delete");
            deleteMark[i].addActionListener(e -> UiUtils.messageBoxOnException(() -> deleteMark(index)));
            manage.add(deleteMark[i]);
            addMarkCell(marks, manage, 5, i + 1);
            fidSelection[i].addActionListener(e -> {
                if (refreshing) {
                    return;
                }
                Surface surface = selectedSurface();
                Mark selected = (Mark) fidSelection[index].getSelectedItem();
                if (surface != null) {
                    catalog.setMark(surface, index, selected == null ? null : selected.getId());
                }
                refreshRow(index);
            });
        }
        addMarkCell(marks, useMachineOrigin, 6, 3);
        useMachineOrigin.addItemListener(e -> {
            if (refreshing) {
                return;
            }
            Surface surface = selectedSurface();
            if (surface != null) {
                catalog.setCUseMachineOrigin(surface, useMachineOrigin.isSelected());
            }
            updateFidCControls();
        });

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
        refreshCatalogUI();
        catalogListener = () -> SwingUtilities.invokeLater(this::refreshCatalogUI);
        catalog.addListener(catalogListener);
    }

    private static void addMarkCell(JPanel panel, Component component, int column, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.insets = new Insets(3, 4, 3, 4);
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = column <= 3 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        constraints.weightx = column == 0 ? 1 : column <= 3 ? 0.6 : 0;
        panel.add(component, constraints);
    }

    private Surface selectedSurface() {
        return catalog.getSurface(feeder.getFiducialSurfaceId());
    }

    private void refreshCatalogUI() {
        refreshing = true;
        try {
            Surface surface = feeder.getOrCreateSurface();
            surfaceBox.setModel(new DefaultComboBoxModel<>(catalog.getSurfaces().toArray(new Surface[0])));
            surfaceBox.setSelectedItem(surface);
            for (int i = 0; i < 3; i++) {
                DefaultComboBoxModel<Mark> model = new DefaultComboBoxModel<>();
                model.addElement(null);
                for (Mark mark : catalog.getMarks()) {
                    model.addElement(mark);
                }
                fidSelection[i].setModel(model);
                fidSelection[i].setSelectedItem(surface == null ? null
                        : catalog.getMark(surface.getMarkId(i)));
                refreshRow(i);
            }
            useMachineOrigin.setSelected(surface != null && surface.isCUseMachineOrigin());
            updateFidCControls();
        }
        finally {
            refreshing = false;
        }
    }

    private void refreshRow(int index) {
        Mark mark = (Mark) fidSelection[index].getSelectedItem();
        if (mark == null) {
            fidX[index].setText("");
            fidY[index].setText("");
        }
        else {
            Helpers.copyLocationIntoTextFields(mark.getLocation(), fidX[index], fidY[index], null);
        }
        updateMark[index].setEnabled(mark != null);
        deleteMark[index].setEnabled(mark != null);
    }

    private void createSurface() {
        String name = JOptionPane.showInputDialog(this, "Installation surface name:",
                "New installation surface", JOptionPane.QUESTION_MESSAGE);
        if (name == null) {
            return;
        }
        Surface surface = catalog.addSurface(name);
        feeder.setFiducialSurfaceId(surface.getId());
        refreshCatalogUI();
    }

    private void renameSurface() {
        Surface surface = selectedSurface();
        if (surface == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Installation surface name:",
                surface.getName());
        if (name != null) {
            catalog.renameSurface(surface.getId(), name);
        }
    }

    private void deleteSurface() {
        Surface surface = selectedSurface();
        if (surface == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete " + surface.getName() + "? Feeders using it must select another surface.",
                "Delete installation surface", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            catalog.removeSurface(surface.getId());
            refreshCatalogUI();
        }
    }

    private Location capturedXY(int index) {
        Length x = Length.parseWithDefaultUnits(fidX[index].getText(),
                Configuration.get().getSystemUnits());
        Length y = Length.parseWithDefaultUnits(fidY[index].getText(),
                Configuration.get().getSystemUnits());
        if (x == null || y == null) {
            throw new IllegalArgumentException("Capture X and Y with the camera controls first.");
        }
        return new Location(LengthUnit.Millimeters,
                x.convertToUnits(LengthUnit.Millimeters).getValue(),
                y.convertToUnits(LengthUnit.Millimeters).getValue(), 0, 0);
    }

    private void saveNewMark(int index) {
        Surface surface = selectedSurface();
        if (surface == null) {
            throw new IllegalArgumentException("Select an installation surface first.");
        }
        Mark mark = catalog.addMark(capturedXY(index));
        catalog.setMark(surface, index, mark.getId());
        refreshCatalogUI();
    }

    private void updateMark(int index) {
        Mark mark = (Mark) fidSelection[index].getSelectedItem();
        if (mark == null) {
            throw new IllegalArgumentException("Select a saved fiducial first.");
        }
        catalog.updateMark(mark.getId(), capturedXY(index));
        refreshCatalogUI();
    }

    private void deleteMark(int index) {
        Mark mark = (Mark) fidSelection[index].getSelectedItem();
        if (mark == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete " + mark.getName() + " from the shared catalog and all surfaces?",
                "Delete fiducial", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            catalog.removeMark(mark.getId());
            refreshCatalogUI();
        }
    }

    private static JTextField disabledOriginField() {
        JTextField field = new JTextField("0.000", 8);
        field.setEnabled(false);
        return field;
    }

    private void updateFidCControls() {
        String card = useMachineOrigin.isSelected() ? "origin" : "custom";
        ((CardLayout) fidCXCards.getLayout()).show(fidCXCards, card);
        ((CardLayout) fidCYCards.getLayout()).show(fidCYCards, card);
        boolean enabled = !useMachineOrigin.isSelected();
        fidControls[2].setEnabled(enabled);
        fidSelection[2].setEnabled(enabled);
        saveMark[2].setEnabled(enabled);
        updateMark[2].setEnabled(enabled && fidSelection[2].getSelectedItem() != null);
        deleteMark[2].setEnabled(enabled && fidSelection[2].getSelectedItem() != null);
    }

    /** Standard OpenPnP camera location buttons, with XY at the top camera's focus height. */
    private static final class FiducialXYControls extends LocationButtonsPanel {
        FiducialXYControls(JTextField x, JTextField y) {
            super(x, y, null, null);
            setShowToolButtons(false);
        }

        @Override
        public Camera getCamera() throws Exception {
            Camera camera = Configuration.get().getMachine().getDefaultHead().getDefaultCamera();
            Length defaultZ = camera.getDefaultZ();
            if (defaultZ == null) {
                throw new Exception("Set the top camera Default Z before moving to a fiducial.");
            }
            setBaseLocation(new Location(defaultZ.getUnits(), 0, 0, defaultZ.getValue(), 0));
            return camera;
        }
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
        DoubleConverter decimal = new DoubleConverter(Configuration.get().getLengthDisplayFormat());
        IntegerConverter integer = new IntegerConverter();
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
        catalog.removeListener(catalogListener);
        if (partsModel != null) {
            partsModel.dispose();
        }
    }
}
