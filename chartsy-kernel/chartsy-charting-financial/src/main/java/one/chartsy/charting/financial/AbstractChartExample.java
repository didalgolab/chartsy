/*
 * Licensed Materials - Property of Rogue Wave Software, Inc. 
 * © Copyright Rogue Wave Software, Inc. 2014, 2017 
 * © Copyright IBM Corp. 2009, 2014
 * © Copyright ILOG 1996, 2009
 * All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * The Software and Documentation were developed at private expense and
 * are "Commercial Items" as that term is defined at 48 CFR 2.101,
 * consisting of "Commercial Computer Software" and
 * "Commercial Computer Software Documentation", as such terms are
 * used in 48 CFR 12.212 or 48 CFR 227.7202-1 through 227.7202-4,
 * as applicable.
 */
package one.chartsy.charting.financial;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.event.MenuDragMouseEvent;
import javax.swing.event.MenuDragMouseListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;

import one.chartsy.charting.Chart;
import one.chartsy.charting.action.ChartAction;

/**
 * This is the abstract base class for all the Charts module demos. It contains
 * much of the common code for handling the menus, the status bar, and so on.
 */
public abstract class AbstractChartExample extends JPanel {

  // =========================================
  // Instance Variables
  // =========================================

  /**
   * The adapter that collects the status text of each menu and displays it in the
   * status bar.
   */
  private final MenuInfo menuStatusAdapter;

  /**
   * The status bar used for showing information when menu items are selected.
   */
  private JLabel status;

  /**
   * The toolbar.
   */
  private JToolBar toolbar;

  /**
   * A blank icon.
   */
  private Icon blankIcon;
  
  /**
   * Content pane.
   */
  private Container contentPane;

  // =========================================
  // Class Constants
  // =========================================

  /**
   * The client-property lookup key for status text of each menu item.
   */
  private static final String STATUS_TEXT_KEY = "StatusText";

  /**
   * The client-property lookup key for whether a <code>JMenu</code> has any menu item icons.
   */
  private static final String MENU_ITEM_ICONS_KEY = "MenuItemIcons";

  /**
   * The blank icon filename.
   */
  // private static final String BLANK_ICON = "images/blank.gif";


  // =========================================
  // Instance Construction and Initialization
  // =========================================

  /**
   * Creates a new <code>AbstractChartExample</code>.
   */
  public AbstractChartExample() {
    menuStatusAdapter = new MenuInfo();
    // try {
    // Image image = ChartUtil.getImageFromFile(AbstractChartExample.class, BLANK_ICON);
    // blankIcon = new ImageIcon(image);
    // } catch(Exception e) {
    // System.err.println("Warning: error while reading image " + BLANK_ICON);
    // }
  }

  /**
   * Sets the location and size of the example's user interface.
   * This method only affects the size and location of the <code>JFrame</code>,
   * when the example is run as an application.
   * The actual size of the toplevel window is such that it is always greater
   * than or equal to its preferred size.
   * @param width The desired width.
   * @param height The desired height.
   * @param centerOnScreen A Boolean value indicating whether the window should
   * be centered on the screen.
   */
  public void setFrameGeometry(final int width, final int height, final boolean centerOnScreen) {
      Window root = SwingUtilities.windowForComponent(this.contentPane);
      if (root != null) {
        Dimension minSize = root.getSize();
        root.setSize(Math.max(width, minSize.width), Math.max(height, minSize.height));
        Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        root.setLocation(d.width / 2 - root.getWidth() / 2, d.height / 2 - root.getHeight() / 2);
      }
   }

  /**
   * Initializes the example's user interface in the specified container. This
   * will be the <code>contentPane</code> of 
   * the <code>JFrame</code>, depending on how the demo is run.
   * Subclasses should override this method to create
   * their user interface and must always invoke super.
   */
  public void init(Container container) {
    this.contentPane = container;
    ((JFrame)SwingUtilities.getWindowAncestor(container)).setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    // Load custom L&Fs using the app's classloader. This works around JavaSoft
    // Bug #4155617, which prevents custom L&Fs from being loaded by the plugin.
    UIManager.put("ClassLoader", getClass().getClassLoader());
  }

  /**
   * Called when the application is about to be closed.
   */
  protected void quit() {}

  /**
   * Initializes the example. Subclasses should not
   * override this method, they should override <code>init(Container)</code> instead.
   */
  public void init() throws InterruptedException, InvocationTargetException {
    SwingUtilities.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        init(AbstractChartExample.this);
      }
    });
  }

  // =========================================
  // Application Permissions
  // =========================================

  /**
   * Returns <code>true</code> if this example should offer printing services.
   */
  protected boolean isPrintingConfigured() {
    return true;
  }

  /**
   * Returns <code>true</code> if this example has permission to print.
   *
   * @return Whether the example has permission to print.
   */
  protected boolean isPrintingAllowed() {
    return true;
  }

  /**
   * Indicates whether this example has permission to access the
   * local file system.
   */
  private Boolean localFileAccessAllowed;

  /**
   * Returns <code>true</code> if this example has permission to access the
   * local file system. 
   *
   * @return Whether the example has permission to access the local file system.
   */
  protected boolean isLocalFileAccessAllowed() {
    if (localFileAccessAllowed == null) {
      localFileAccessAllowed = Boolean.TRUE;
    }
    return localFileAccessAllowed.booleanValue();
  }


  // =========================================
  // Accessing Resources
  // =========================================

  /**
   * Returns the URL of a resource file relatively to the working directory
   * of the example.
   * @param relativePath The relative path to the resource file.
   * @return The resource URL, or <code>null</code> if the resource cannot be found.
   */
  public URL getResourceURL(String relativePath) {
    URL url = null;
    try {
      url = new java.io.File(relativePath).toURI().toURL();
    } catch (MalformedURLException x) {
      System.err
          .println("Cannot find resource file: " + relativePath + " (" + x.getMessage() + ")");
      x.printStackTrace();
    }
    return url;
  }


  // =========================================
  // Status Bar
  // =========================================

  /**
   * Sets the status text of the specified component.
   */
  protected void setStatusText(JComponent component, String text) {
    component.putClientProperty(STATUS_TEXT_KEY, text);
    if (component instanceof JMenuItem)
      ((JMenuItem) component).addMenuDragMouseListener(menuStatusAdapter);
    if (component instanceof JMenu)
      ((JMenu) component).addMenuListener(menuStatusAdapter);
  }

  /**
   * Returns the status text of the specified component.
   */
  protected String getStatusText(JComponent component) {
    return (String) component.getClientProperty(STATUS_TEXT_KEY);
  }

  /**
   * Creates the status bar component.
   */
  protected JLabel createStatusBar() {
    status = new JLabel(" ");
    status.setBorder(new BevelBorder(BevelBorder.LOWERED));
    return status;
  }

  /**
   * Returns the status bar component.
   */
  protected JLabel getStatusBar() {
    return status;
  }


  // =========================================
  // ToolBar
  // =========================================

  /**
   * Creates the toolbar component.
   */
  protected JToolBar createToolBar() {
    toolbar = new JToolBar();
    toolbar.setFloatable(false);
    populateToolBar(toolbar);
    return toolbar;
  }

  /**
   * Populates the specified toolbar. This default implementation does nothing.
   * Subclasses should override this method as needed.
   */
  protected void populateToolBar(JToolBar toolbar) {}

  /**
   * Returns the status bar component.
   */
  protected JToolBar getToolBar() {
    return toolbar;
  }


  // =========================================
  // Menus
  // =========================================

  /**
   * Sets the menu bar for the top-level ancestor of the demo: the
   * containing <code>JFrame</code>.
   */
  protected void setMenuBar(JMenuBar menuBar) {
    Window topComponent = SwingUtilities.windowForComponent(this.contentPane);
    if (topComponent instanceof JFrame)
      ((JFrame) topComponent).setJMenuBar(menuBar);
  }

  /**
   * Returns the menu bar for the top-level ancestor of the demo:
   * the containing <code>JFrame</code>.
   */
  protected JMenuBar getMenuBar() {
    Window topComponent = SwingUtilities.windowForComponent(this.contentPane);
    if (topComponent instanceof JFrame)
      return ((JFrame) topComponent).getJMenuBar();
    else
      return null;
  }

  /**
   * Returns the blank icon that can be used to left-align menu items that do
   * not have an icon of their own.
   */
  protected Icon getBlankIcon() {
    return blankIcon;
  }

  /**
   * Calculates whether the specified menu has any menu items with icons.
   */
  private boolean computeHasIcons(JMenu menu) {
    for (int i = 0; i < menu.getMenuComponentCount(); i++) {
      Component c = menu.getMenuComponent(i);
      if (c instanceof AbstractButton && ((AbstractButton) c).getIcon() != null)
        return true;
    }
    return false;
  }

  /**
   * Sets whether the specified menu has any menu items with icons.
   */
  protected void setHasIcons(JMenu menu, boolean flag) {
    menu.putClientProperty(MENU_ITEM_ICONS_KEY, flag);
  }

  /**
   * Returns whether the specified menu has any menu items with icons.
   */
  protected boolean hasIcons(JMenu menu) {
    Boolean flag = (Boolean) menu.getClientProperty(MENU_ITEM_ICONS_KEY);
    if (flag == null) {
      flag = computeHasIcons(menu);
      menu.putClientProperty(MENU_ITEM_ICONS_KEY, flag);
    }
    return flag.booleanValue();
  }

  /**
   * Adds the specified action to a menu and returns the created <code>JMenuItem</code>.
   */
  protected JMenuItem addAction(JMenu menu, ChartAction action) {
    // If we are about to add the first item with an icon, set all other items
    // to have a blank icon for proper alignment.
    if (!hasIcons(menu) && action.getIcon() != null) {
      for (int i = 0; i < menu.getMenuComponentCount(); i++) {
        Component c = menu.getMenuComponent(i);
        if (c instanceof AbstractButton && ((AbstractButton) c).getIcon() != null)
          ((AbstractButton) c).setIcon(getBlankIcon());
      }
      setHasIcons(menu, true);
    }
    // Create the menu item from the action.
    JMenuItem menuItem = menu.add(action);
    // If we have added an action with no icon to a menu that has icons,
    // use a blank icon for the menu item so that it is properly aligned.
    if (hasIcons(menu) && menuItem.getIcon() == null)
      menuItem.setIcon(getBlankIcon());
    KeyStroke accelerator = action.getAccelerator();
    if (accelerator != null)
      menuItem.setAccelerator(accelerator);
    String longDescription = action.getLongDescription();
    if (longDescription == null)
      longDescription = " ";
    setStatusText(menuItem, longDescription);
    return menuItem;
  }

  /**
   * Adds the specified action to a toolbar and returns the created <code>JButton</code>.
   */
  protected JButton addAction(JToolBar toolbar, ChartAction action) {
    JButton button = toolbar.add(action);
    // Only use text on buttons if there is no icon.
    if (button.getIcon() != null)
      button.setText(null);
    button.setPreferredSize(new Dimension(22, 22));
    KeyStroke accelerator = action.getAccelerator();
    if (accelerator != null)
      button.registerKeyboardAction(action, accelerator, JButton.WHEN_IN_FOCUSED_WINDOW);
    String shortDescription = action.getShortDescription();
    if (shortDescription != null)
      button.setToolTipText(shortDescription);
    return button;
  }


  // =========================================
  // Printing and PDF Generation
  // =========================================

  /**
   * The file chooser for saving.
   */
  private JFileChooser filechooser;

  /**
   * Utility function: Creates a file filter for a JFileChooser.
   */
  private static FileFilter createFileFilter(final String[] extensions,
      final String extensionsDescription) {
    return new FileFilter() {
      @Override
      public boolean accept(File f) {
        if (f.isDirectory())
          return true;
        String last = f.getName();
        int lastDot = last.lastIndexOf('.');
        if (lastDot >= 0) {
          String ext = last.substring(lastDot);
          for (int i = 0; i < extensions.length; i++)
            if (extensions[i].equals(ext))
              return true;
        }
        return false;
      }

      @Override
      public String getDescription() {
        return extensionsDescription;
      }
    };
  }


  // =========================================
  // Look and Feel
  // =========================================

  /**
   * Sets the look-and-feel.
   */
  protected void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
    try {
      UIManager.setLookAndFeel(laf.getClassName());
      Window topComponent = SwingUtilities.windowForComponent(this.contentPane);
      SwingUtilities.updateComponentTreeUI(topComponent);
    } catch (Exception e) {
      System.err.println("Failed to install " + laf.getName() + "L&F\n" + e);
    }
  }

  /**
   * Creates a menu that allows the user to switch between the
   * available look-and-feels.
   */
  protected JMenu createLAFMenu() {
    JMenu lafMenu = new JMenu("LookAndFeel");
    lafMenu.setMnemonic(KeyEvent.VK_O);
    setStatusText(lafMenu, "Selects a Look-and-Feel for the demo.");
    ButtonGroup lafGroup = new ButtonGroup();
    ItemListener menuListener = new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent evt) {
        JRadioButtonMenuItem button = (JRadioButtonMenuItem) evt.getSource();
        if (button.isSelected()) {
          final UIManager.LookAndFeelInfo selectedLF =
              (UIManager.LookAndFeelInfo) button.getClientProperty("UIKey");
          setLookAndFeel(selectedLF);
        }
      }
    };
    // Get the list of look-and-feels from the swing.properties file.
    UIManager.LookAndFeelInfo[] lafInfo = UIManager.getInstalledLookAndFeels();
    for (int i = 0; i < lafInfo.length; i++) {
      String lafName = lafInfo[i].getName();
      String lafClassName = lafInfo[i].getClassName();
      // We need to verify that the l&f actually exists and is supported.
      boolean lafSupported = false;
      try {
        Class<?> lafClass = Class.forName(lafClassName);
        LookAndFeel laf = (LookAndFeel) lafClass.newInstance();
        lafSupported = laf.isSupportedLookAndFeel();
      } catch (Throwable t) {
      }
      if (lafSupported) {
        JRadioButtonMenuItem rb = new JRadioButtonMenuItem(lafName);
        lafMenu.add(rb);
        rb.setSelected(UIManager.getLookAndFeel().getName().equals(lafName));
        rb.putClientProperty("UIKey", lafInfo[i]);
        rb.addItemListener(menuListener);
        lafGroup.add(rb);
      }
    }
    return lafMenu;
  }


  // =========================================
  // Colors
  // =========================================

  /**
   * Shows a modal color-chooser dialog box and blocks until the dialog box is hidden.
   * If the user presses the "OK" button, this method hides/disposes the
   * dialog box and returns the selected color. If the user presses the "Cancel"
   * button or closes the dialog box without pressing "OK", this method
   * hides/disposes the dialog box and returns <code>null</code>.
   * @param title The title of the dialog box.
   * @param initialColor The initial color set when the color-chooser is shown.
   */
  protected Color chooseColor(String title, Color initialColor) {
    return JColorChooser.showDialog(this, title, initialColor);
  }


  // =========================================
  // Support for Dialogs
  // =========================================

  /**
   * Returns a window (normally a frame) that is suitable as owner for dialogs.
   */
  protected Window getOwnerWindow() {
    return SwingUtilities.windowForComponent(this.contentPane);
  }


  // =========================================
  // Support for Displaying Online Help
  // =========================================



  // =========================================
  // Inner Classes
  // =========================================

  /**
   * <code>MenuInfo</code> is an inner class used for displaying information on menus and menu
   * items. The status text of the menu item is displayed in the status bar.
   * This class should be registered as a <code>MenuDragMouseListener</code> with <code>JMenuItem</code>s
   * and as both a <code>MenuDragMouseListener</code> and a <code>MenuListener</code> with <code>JMenu</code>s.
   */
  class MenuInfo implements MenuDragMouseListener, MenuListener {

    // Text used to clear the status bar.
    private final String NULL_TEXT = " ";

    // The current text being displayed.
    private String currentText = NULL_TEXT;

    // Clears the status bar.
    private void clearStatus() {
      JLabel statusBar = getStatusBar();
      if (statusBar == null)
        return;
      if (currentText != NULL_TEXT) {
        currentText = NULL_TEXT;
        statusBar.setText(NULL_TEXT);
      }
    }

    // Sets the status bar based on the source of the specified event.
    private void setStatus(java.util.EventObject e) {
      JLabel statusBar = getStatusBar();
      if (statusBar == null)
        return;
      Object source = e.getSource();
      if (source instanceof JComponent) {
        String str = getStatusText((JComponent) source);
        if (str == null || str.equals(""))
          clearStatus();
        else if (!currentText.equals(str)) {
          currentText = str;
          statusBar.setText(str);
        }
      } else
        clearStatus();
    }

    // A JMenu has been cancelled. Clears the status bar.
    @Override
    public void menuCanceled(MenuEvent e) {
      clearStatus();
    }

    // A JMenu has been deselected. Clears the status bar.
    @Override
    public void menuDeselected(MenuEvent e) {
      clearStatus();
    }

    // A JMenu has been selected. Sets the status bar to its tooltip text, if any.
    @Override
    public void menuSelected(MenuEvent e) {
      setStatus(e);
    }

    // The user has moved the mouse into a menu element. Sets the status bar to
    // its tooltip text, if any.
    @Override
    public void menuDragMouseEntered(MenuDragMouseEvent e) {
      setStatus(e);
    }

    // The user has moved the mouse out of a menu element. Clears the status bar.
    @Override
    public void menuDragMouseExited(MenuDragMouseEvent e) {
      clearStatus();
    }

    // The user has moved the mouse within a menu element. Sets the status bar to
    // its tooltip text, if any.
    @Override
    public void menuDragMouseDragged(MenuDragMouseEvent e) {
      setStatus(e);
    }

    @Override
    public void menuDragMouseReleased(MenuDragMouseEvent e) {}
  }

  /**
   * <code>ColorSwatch</code> implements an icon filled with a solid color.
   */
  public static class ColorSwatch implements Icon {

    private static final int DEFAULT_SIZE = 16;
    private final int width;
    private final int height;
    private final Color color;

    public ColorSwatch(Color c) {
      this(c, DEFAULT_SIZE);
    }

    public ColorSwatch(Color c, int size) {
      this(c, size, size);
    }

    public ColorSwatch(Color c, int width, int height) {
      this.color = c;
      this.width = width;
      this.height = height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Color oldColor = g.getColor();
      g.setColor(color);
      g.fill3DRect(x, y, getIconWidth(), getIconHeight(), true);
      g.setColor(oldColor);
    }

    @Override
    public int getIconWidth() {
      return width;
    }

    @Override
    public int getIconHeight() {
      return height;
    }

  }

}

