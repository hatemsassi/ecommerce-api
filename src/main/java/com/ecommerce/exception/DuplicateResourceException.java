package com.ecommerce.exception;

public class DuplicateResourceException extends RuntimeException {

	public DuplicateResourceException(String resource, String field, Object value) {
		super("%s already exists with %s: %s".formatted(resource, field, value));
	}
}
