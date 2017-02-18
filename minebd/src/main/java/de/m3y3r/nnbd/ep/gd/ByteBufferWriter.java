package de.m3y3r.nnbd.ep.gd;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;

public class ByteBufferWriter implements MessageBodyWriter<ByteBuffer> {

	@Override
	public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return ByteBuffer.class.isAssignableFrom(type);
	}

	@Override
	public long getSize(ByteBuffer t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	@Override
	public void writeTo(ByteBuffer t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
			throws IOException, WebApplicationException {
		while(t.hasRemaining()) {
			entityStream.write(t.get());
		}
	}
}
