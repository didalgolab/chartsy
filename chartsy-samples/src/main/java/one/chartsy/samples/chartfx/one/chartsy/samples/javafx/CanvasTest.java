/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.one.chartsy.samples.javafx;

import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Ref: https://stackoverflow.com/questions/62246642/javafx-avoid-blur-when-drawing-with-rect-on-canvas-with-highdpi
 */
public class CanvasTest extends Application {
    public static class Starter {
        public static void main(String[] args) {
            CanvasTest.main(args);
        }
    }

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        BorderPane borderPane = new BorderPane();

        HBox hbox = new HBox();

        Canvas canvas = new Canvas(100,100);

        GraphicsContext g2d = canvas.getGraphicsContext2D();
        g2d.setImageSmoothing(false);

        double s = 1.0;
        g2d.fillRect(15 * s, 15 * s, 60 * s, 10 * s);
        g2d.fillRect(15.5 * s, 30 * s, 60 * s, 10 * s);  // supposed to be blurry
        g2d.fillRect(16 * s, 45 * s, 60 * s, 10 * s);
        g2d.fillRect(16.5 * s, 60 * s, 60 * s, 10 * s);  // supposed to be blurry

//        g2d.setLineWidth(3.0 / 1.25);
        g2d.setStroke(Color.RED);
//        g2d.strokeLine(13.5 * (1.0/1.25), 13.5 * (1.0/1.25), 13.5 * (1.0/1.25), 93.5 * s);
//        g2d.strokeLine(13.5 * s, 13.5 * s, 93.5 * s, 93.5 * s);
        g2d.strokeLine(13. * s + .5, 13. * s + .5, 13. * s + .5, 93. * s + .5);
        g2d.strokeLine(13. * s + .5, 13. * s + .5, 93. * s + .5, 93. * s + .5);
        hbox.getChildren().add(new Group(canvas));

        borderPane.setCenter(hbox);
        ScrollBar scrollBar = new ScrollBar();
        //scrollBar.setOrientation(Orientation.HORIZONTAL);
        scrollBar.setPrefHeight(40.0);
        borderPane.setBottom(scrollBar);
        Scene scene = new Scene(borderPane);

        stage.setScene(scene);
        stage.show();

        System.out.println(scene.getWindow().outputScaleXProperty());
//        borderPane.scaleXProperty().bind(new SimpleDoubleProperty(1.0).divide(scene.getWindow().outputScaleXProperty()));
//        borderPane.scaleYProperty().bind(new SimpleDoubleProperty(1.0).divide(scene.getWindow().outputScaleYProperty()));
    }
}