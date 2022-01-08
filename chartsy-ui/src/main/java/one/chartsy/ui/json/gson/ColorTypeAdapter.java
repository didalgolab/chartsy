package one.chartsy.ui.json.gson;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.awt.*;
import java.io.IOException;

public class ColorTypeAdapter extends TypeAdapter<Color> {

	@Override
	public void write(JsonWriter out, Color value) throws IOException {
		if (value == null) {
			out.nullValue();
		} else {
			int red = value.getRed();
			int green = value.getGreen();
			int blue = value.getBlue();
			int alpha = value.getAlpha();

			String colorStr;
			if (alpha != 255)
				colorStr = String.format("#%02x%02x%02x%02x", red, green, blue, alpha);
			else
				colorStr = String.format("#%02x%02x%02x", red, green, blue);
			out.value(colorStr);
		}
	}

	@Override
	public Color read(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL) {
			in.nextNull();
			return null;
		}
		String value = in.nextString();
		try {
			if (!value.startsWith("#"))
				throw new JsonParseException("Invalid color token: " + value + " at " + in.getPath());

			int i = Integer.decode(value);
			if (value.length() == 9)
				return new Color((i >> 24) & 0xFF, (i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
			else if (value.length() == 7)
				return new Color((i >> 16) & 0xFF, (i >> 8) & 0xFF, i & 0xFF);
			else
				throw new JsonParseException("Invalid color token: " + value + " at " + in.getPath());
		} catch (IllegalArgumentException e) {
			throw new JsonParseException("Invalid color token: " + value + " at " + in.getPath(), e);
		}
	}
}
