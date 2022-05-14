/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.json.gson;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import one.chartsy.ui.chart.BasicStrokes;

import java.awt.*;
import java.io.IOException;

/**
 * A basic {@link TypeAdapter} for JavaFX {@link Color}. It serializes a color as its RGBA hexadecimal string
 * representation preceded by #. Supports null values.
 */
public class BasicStrokeTypeAdapter extends TypeAdapter<Stroke> {

	@Override
	public void write(JsonWriter out, Stroke value) throws IOException {
		if (value == null) {
			out.nullValue();
			return;
		}
		out.value(BasicStrokes.getStrokeName(value).get());
	}

	@Override
	public Stroke read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		String value = in.nextString();
		try {
			return BasicStrokes.getStroke(value);
		} catch (IllegalArgumentException e) {
			throw new JsonParseException("Invalid color token: " + value + " at " + in.getPath());
		}
	}
}
