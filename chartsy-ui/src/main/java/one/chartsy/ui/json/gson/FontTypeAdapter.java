/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.json.gson;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.awt.*;
import java.io.IOException;

public class FontTypeAdapter extends TypeAdapter<Font> {

	@Override
	public void write(JsonWriter out, Font value) throws IOException {
		if (value == null) {
			out.nullValue();
			return;
		}
		StringBuilder buff = new StringBuilder();
		buff.append(value.getName());
		buff.append('-').append(switch(value.getStyle()) {
			default -> "PLAIN";
			case Font.PLAIN -> "PLAIN";
			case Font.BOLD -> "BOLD";
			case Font.ITALIC -> "ITALIC";
			case Font.BOLD | Font.ITALIC -> "BOLDITALIC";
		});
		buff.append('-').append(value.getSize());

		out.value(buff.toString());
	}

	@Override
	public Font read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		String value = in.nextString();
		try {
			return Font.decode(value);
		} catch (IllegalArgumentException e) {
			throw new JsonParseException("Invalid font: " + value + " at " + in.getPath());
		}
	}
}