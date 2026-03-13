package one.chartsy.charting.action;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.CoordinateSystem;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;

/// Base class for Swing actions that operate on a bound [Chart] and one of its y-axis slots.
///
/// The class keeps ordinary Swing action metadata such as name, icon, accelerator, and
/// descriptions, then adds chart-specific binding and lookup helpers used by chart menus,
/// toolbars, and keyboard commands. Subclasses usually override [#computeEnabled()] to reflect
/// whether the current chart state admits the command and implement the command body in
/// `actionPerformed(...)`.
///
/// Axis lookup helpers are fail-soft. [#getCoordinateSystem()], [#getYAxis()], and [#getXAxis()]
/// return `null` when the action is detached, the selected y axis does not exist, or the chart
/// cannot currently supply the requested object.
///
/// Instances are mutable UI objects and are not thread-safe.
///
/// ### Implementation Requirements
///
/// Constructors call [#computeEnabled()] before subclass fields finish initializing. Overrides
/// should therefore tolerate being called while detached and before subclass-specific state is
/// fully available.
public abstract class ChartAction extends AbstractAction {
  @Serial
  private static final long serialVersionUID = 1L;

  private Chart chart;
  private int yAxisIndex = 0;

  // ---------------------------------------------------------------------------
  // Constructors
  // ---------------------------------------------------------------------------

  /// Creates a detached action with a display name.
  public ChartAction(String name) {
    super(name);
    computeEnabled();
  }

  /// Creates a detached action with a display name and icon metadata.
  public ChartAction(String name, Icon icon) {
    super(name, icon);
    computeEnabled();
  }

  /// Creates a detached action with name, icon, and accelerator metadata.
  ///
  /// The accelerator is stored as action metadata only. A menu, button, or other UI binder must
  /// still register it on the component that should react to the keystroke.
  public ChartAction(String name, Icon icon, KeyStroke accelerator) {
    super(name, icon);
    setAccelerator(accelerator);
    computeEnabled();
  }

  /// Creates a detached action with name, icon, accelerator, and description metadata.
  ///
  /// The action starts detached, so the default [#computeEnabled()] implementation leaves it
  /// disabled until [#setChart(Chart)] binds a chart.
  public ChartAction(
      String name,
      Icon icon,
      KeyStroke accelerator,
      String shortDescription,
      String longDescription) {
    super(name, icon);
    setAccelerator(accelerator);
    setShortDescription(shortDescription);
    setLongDescription(longDescription);
    computeEnabled();
  }

  // ---------------------------------------------------------------------------
  // Attachment lifecycle
  // ---------------------------------------------------------------------------

  /// Returns the chart currently bound to this action, or `null` while detached.
  public final Chart getChart() {
    return chart;
  }

  /// Rebinds this action to a chart and refreshes its enabled state.
  ///
  /// When an action is already attached, [#detach()] runs before the stored chart reference is
  /// replaced. If `chart` is non-null, [#attach()] then runs against the new reference and
  /// [#computeEnabled()] is invoked last.
  ///
  /// @param chart the new chart owner, or `null` to detach
  public void setChart(Chart chart) {
    if (this.chart != null) {
      detach();
    }
    this.chart = chart;
    if (this.chart != null) {
      attach();
    }
    computeEnabled();
  }

  /// Hook called immediately before the current chart reference is cleared or replaced.
  ///
  /// During this callback [#getChart()] still returns the chart being detached. The default
  /// implementation does nothing.
  protected void detach() {
    // no-op by default
  }

  /// Hook called immediately after a new chart reference has been stored.
  ///
  /// During this callback [#getChart()] already returns the newly bound chart. The default
  /// implementation does nothing.
  protected void attach() {
    // no-op by default
  }

  /// Recomputes whether this action should currently be enabled.
  ///
  /// The base implementation enables the action only while a chart is attached.
  ///
  /// ### Implementation Requirements
  ///
  /// Overrides may be invoked during superclass construction, after [#setChart(Chart)], and after
  /// [#setYAxisIndex(int)]. They should therefore tolerate a detached chart and partially
  /// initialized subclass state. Subclasses typically call `super.computeEnabled()` first and then
  /// apply narrower command-specific checks.
  protected void computeEnabled() {
    setEnabled(this.chart != null);
  }

  // ---------------------------------------------------------------------------
  // Axis and coordinate system helpers
  // ---------------------------------------------------------------------------

  /// Returns the zero-based y-axis slot currently targeted by this action.
  public int getYAxisIndex() {
    return yAxisIndex;
  }

  /// Selects the y-axis slot used by this action's axis lookup helpers.
  ///
  /// The value is stored verbatim and is not validated eagerly. Helpers that need a real y axis
  /// return `null` when the bound chart has no matching slot.
  ///
  /// @param yAxisIndex the zero-based y-axis slot this action should target
  public void setYAxisIndex(int yAxisIndex) {
    this.yAxisIndex = yAxisIndex;
    computeEnabled();
  }

  /// Returns the coordinate system pairing the selected y axis with the chart's shared x axis.
  ///
  /// This helper suppresses runtime lookup failures and returns `null` instead, which lets callers
  /// treat temporary chart reconfiguration as "action unavailable".
  public final CoordinateSystem getCoordinateSystem() {
    if (chart == null) {
      return null;
    }
    try {
      return chart.getCoordinateSystem(yAxisIndex);
    } catch (RuntimeException ex) {
      // If the chart cannot provide the coordinate system, fail gracefully.
      return null;
    }
  }

  /// Returns the selected y axis, or `null` when it is currently unavailable.
  ///
  /// A `null` result means the action is detached, the stored y-axis slot is out of range, or the
  /// chart rejected the lookup while reconfiguring.
  public final Axis getYAxis() {
    if (chart == null) {
      return null;
    }
    try {
      int count = chart.getYAxisCount();
      if (yAxisIndex < 0 || yAxisIndex >= count) {
        return null;
      }
      return chart.getYAxis(yAxisIndex);
    } catch (RuntimeException ex) {
      // If the chart cannot provide the y-axis, fail gracefully.
      return null;
    }
  }

  /// Returns the bound chart's shared x axis, or `null` when it is currently unavailable.
  public final Axis getXAxis() {
    if (chart == null) {
      return null;
    }
    try {
      return chart.getXAxis();
    } catch (RuntimeException ex) {
      // If the chart cannot provide the x-axis, fail gracefully.
      return null;
    }
  }

  // ---------------------------------------------------------------------------
  // Icon handling
  // ---------------------------------------------------------------------------

  /// Returns the value currently stored under Swing's `SMALL_ICON` key as an [Icon].
  public Icon getIcon() {
    return (getValue(SMALL_ICON) instanceof Icon icon) ? icon : null;
  }

  /// Stores the small icon metadata used when UI code renders this action with an icon.
  public void setIcon(Icon icon) {
    putValue(SMALL_ICON, icon);
  }

  /// Loads the small icon from a classpath resource.
  ///
  /// The resource is resolved relative to `resourceBase`. Missing or unreadable resources clear
  /// the current icon instead of throwing.
  ///
  /// @param resourceBase the class whose package anchors resource lookup
  /// @param resourcePath the classpath-relative resource to load
  public void setIcon(Class<?> resourceBase, String resourcePath) {
    if (resourceBase == null || resourcePath == null) {
      setIcon(null);
      return;
    }
    InputStream resourceStream = resourceBase.getResourceAsStream(resourcePath);
    if (resourceStream == null) {
      setIcon(null);
      return;
    }
    try (InputStream input = resourceStream) {
      BufferedImage image = ImageIO.read(input);
      setIcon(image != null ? new ImageIcon(image) : null);
    } catch (IOException ex) {
      setIcon(null);
    }
  }

  // ---------------------------------------------------------------------------
  // Accelerator handling
  // ---------------------------------------------------------------------------

  /// Returns the value currently stored under Swing's `ACCELERATOR_KEY` as a [KeyStroke].
  public KeyStroke getAccelerator() {
    return (getValue(ACCELERATOR_KEY) instanceof KeyStroke accelerator) ? accelerator : null;
  }

  /// Returns a display label for the current accelerator, or `null` when no accelerator is set.
  ///
  /// The label is assembled from `InputEvent.getModifiersExText(...)` and
  /// `KeyEvent.getKeyText(...)` and is meant for UI display only, not parsing or persistence.
  public String getAcceleratorText() {
    KeyStroke accelerator = getAccelerator();
    if (accelerator == null) {
      return null;
    }
    StringBuilder text = new StringBuilder();
    String modifierText = InputEvent.getModifiersExText(accelerator.getModifiers());
    if (!modifierText.isEmpty()) {
      text.append(modifierText).append("+");
    }
    int keyCode = accelerator.getKeyCode();
    if (keyCode != 0) {
      text.append(KeyEvent.getKeyText(keyCode));
    } else {
      char keyChar = accelerator.getKeyChar();
      if (keyChar != KeyEvent.CHAR_UNDEFINED) {
        text.append(Character.toUpperCase(keyChar));
      } else {
        // Fallback to KeyStroke.toString() if we cannot determine a label
        text.append(accelerator);
      }
    }
    return text.toString();
  }

  /// Stores the keyboard accelerator metadata for this action.
  ///
  /// This method updates the Swing action value only. Menus, buttons, and other component binders
  /// must still install or register the keystroke on the component that should react to it.
  ///
  /// @param accelerator the keystroke that should represent this action, or `null` to clear it
  public void setAccelerator(KeyStroke accelerator) {
    putValue(ACCELERATOR_KEY, accelerator);
  }

  // ---------------------------------------------------------------------------
  // Descriptions
  // ---------------------------------------------------------------------------

  /// Returns the tooltip text currently stored under Swing's `SHORT_DESCRIPTION` key.
  public String getShortDescription() {
    return (getValue(SHORT_DESCRIPTION) instanceof String shortDescription) ? shortDescription : null;
  }

  /// Stores the tooltip text used by toolbar-style renderers of this action.
  ///
  /// @param shortDescription the tooltip text, or `null` to clear it
  public void setShortDescription(String shortDescription) {
    putValue(SHORT_DESCRIPTION, shortDescription);
  }

  /// Returns the long description currently stored under Swing's `LONG_DESCRIPTION` key.
  ///
  /// In the legacy chart demos this value is used as menu-hover status text.
  public String getLongDescription() {
    return (getValue(LONG_DESCRIPTION) instanceof String longDescription) ? longDescription : null;
  }

  /// Stores the long description used by status-line and menu-hover UIs.
  ///
  /// @param longDescription the long description text, or `null` to clear it
  public void setLongDescription(String longDescription) {
    putValue(LONG_DESCRIPTION, longDescription);
  }
}
