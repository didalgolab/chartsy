/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

public class BufferTooSmallException extends IndexOutOfBoundsException {
	private final int actualCapacity;
	private final int requiredCapacity;

	public BufferTooSmallException(String message, int actualCapacity, int requiredCapacity) {
		super(message + " - required capacity: " + requiredCapacity + ", actual: " + actualCapacity);
		this.actualCapacity = actualCapacity;
		this.requiredCapacity = requiredCapacity;
	}

	public int getActualCapacity() {
		return actualCapacity;
	}

	public int getRequiredCapacity() {
		return requiredCapacity;
	}
}
