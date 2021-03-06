package com.conveyal.r5.util;

import com.axiomalaska.polylineencoder.EncodedPolyline;
import com.axiomalaska.polylineencoder.PolylineEncoder;
import com.axiomalaska.polylineencoder.UnsupportedGeometryTypeException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.vividsolutions.jts.geom.LineString;

import java.io.IOException;

/**
 * Serialize to Google encoded polyline.
 */
public class EncodedPolylineSerializer extends JsonSerializer<LineString> {

    @Override
    public void serialize(LineString lineString, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        try {
            String points = PolylineEncoder.encode(lineString).getPoints();
            jsonGenerator.writeString(points);
        } catch (UnsupportedGeometryTypeException e) {
            throw new RuntimeException(e);
        }
    }
}
