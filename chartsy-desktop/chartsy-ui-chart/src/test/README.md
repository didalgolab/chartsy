# Chart UI tests

`LegendStudyChooserIntegrationTest` runs with the normal Maven test suite. It
checks actual modal dialogs through the chart's Swing component hierarchy and
action lookup, including preselection of duplicate study instances and closing
without replacing chart studies.

These three tests need a graphical display. JUnit reports them as skipped in
headless environments; the other legend and chooser tests still run. A desktop
session or a virtual display such as Xvfb is required to exercise the real-dialog
checks in CI. The class runs in isolation from other JUnit tests, uses a private
window owner, and automatically closes its dialogs on success or failure.

From the Chartsy repository root, with Java 25, run only these integration tests:

```shell
mvn -T 1C -pl chartsy-desktop/chartsy-ui-chart -am -Dtest=LegendStudyChooserIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine="-Xmx4g -Djava.awt.headless=false" test
```

To run the UI module and its dependencies:

```shell
mvn -T 1C -pl chartsy-desktop/chartsy-ui-chart -am -DargLine=-Xmx4g test
```

Allow up to 20 minutes for either Maven reactor build. Each integration case
uses a five-second dialog-observation deadline and a 20-second JUnit timeout.
The tests dispatch Swing mouse events; they do not automate the OS mouse.
