/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2026 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.cmemory;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Simulated C heap backing the low-level C bricks (malloc, calloc, realloc, free,
 * memcpy, memset, pointer set/get, cast, typedef).
 *
 * Pointers are plain numeric addresses stored in ordinary Catrobat user variables,
 * exactly like in the standalone C interpreter (c_runtime/). No real process memory
 * is ever exposed: every access is bounds-checked against the allocation that owns
 * the address. Semantics mirror c_runtime/src/cat_interpreter.c one to one.
 */
public class CMemory implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final long ADDRESS_BASE = 0x1000;
	public static final int MAX_TOTAL_BYTES = 16 * 1024 * 1024;
	private static final int TYPEDEF_MAX_HOPS = 8;

	private static class Allocation implements Serializable {
		private static final long serialVersionUID = 1L;

		long address;
		byte[] data;
	}

	private final Map<Long, Allocation> allocations = new HashMap<>();
	private final Map<String, String> typedefs = new HashMap<>();
	private long nextAddress = ADDRESS_BASE;
	private long totalBytes = 0;

	public long malloc(int size) {
		if (size <= 0 || totalBytes + size > MAX_TOTAL_BYTES) {
			return 0;
		}
		Allocation allocation = new Allocation();
		allocation.address = nextAddress;
		allocation.data = new byte[size];
		nextAddress += (size + 15) & ~15;
		totalBytes += size;
		allocations.put(allocation.address, allocation);
		return allocation.address;
	}

	public long calloc(int count, int size) {
		if (count <= 0 || size <= 0 || count > MAX_TOTAL_BYTES || size > MAX_TOTAL_BYTES
				|| (long) count * (long) size > MAX_TOTAL_BYTES) {
			return 0;
		}
		long address = malloc(count * size);
		if (address != 0) {
			Allocation allocation = allocations.get(address);
			java.util.Arrays.fill(allocation.data, (byte) 0);
		}
		return address;
	}

	public long realloc(long oldAddress, int size) {
		if (size <= 0) {
			free(oldAddress);
			return 0;
		}
		Allocation old = findOwner(oldAddress);
		long address = malloc(size);
		if (address == 0) {
			return 0;
		}
		if (old != null) {
			Allocation fresh = allocations.get(address);
			System.arraycopy(old.data, 0, fresh.data, 0, Math.min(old.data.length, size));
			allocations.remove(oldAddress);
		}
		return address;
	}

	public void free(long address) {
		Allocation allocation = allocations.remove(address);
		if (allocation != null) {
			totalBytes -= allocation.data.length;
		}
	}

	public void memset(long address, int value, int size) {
		Allocation allocation = findOwner(address);
		if (allocation == null || size <= 0) {
			return;
		}
		int offset = (int) (address - allocation.address);
		int n = clampSize(offset, size, allocation.data.length);
		byte fill = (byte) value;
		for (int i = 0; i < n; i++) {
			allocation.data[offset + i] = fill;
		}
	}

	public void memcpy(long destination, long source, int size) {
		Allocation dst = findOwner(destination);
		Allocation src = findOwner(source);
		if (dst == null || src == null || size <= 0) {
			return;
		}
		int dstOffset = (int) (destination - dst.address);
		int srcOffset = (int) (source - src.address);
		int n = clampSize(dstOffset, size, dst.data.length);
		n = clampSize(srcOffset, n, src.data.length);
		if (n > 0) {
			System.arraycopy(src.data, srcOffset, dst.data, dstOffset, n);
		}
	}

	public void pointerSet(long address, int offset, Object value, String type) {
		Allocation allocation = findOwner(address);
		if (allocation == null) {
			return;
		}
		String resolved = resolveType(type);
		int size = typeSize(resolved);
		int innerOffset = (int) (address - allocation.address) + offset;
		size = clampSize(innerOffset, size, allocation.data.length);
		if (size <= 0) {
			return;
		}
		encode(allocation.data, innerOffset, size, resolved, value);
	}

	public Object pointerGet(long address, int offset, String type) {
		Allocation allocation = findOwner(address);
		if (allocation == null) {
			return 0d;
		}
		String resolved = resolveType(type);
		int size = typeSize(resolved);
		int innerOffset = (int) (address - allocation.address) + offset;
		size = clampSize(innerOffset, size, allocation.data.length);
		if (size <= 0) {
			return 0d;
		}
		return decode(allocation.data, innerOffset, size, resolved);
	}

	public void defineTypedef(String alias, String baseType) {
		if (alias == null || alias.isEmpty() || baseType == null || baseType.isEmpty()) {
			return;
		}
		typedefs.put(alias, baseType);
	}

	public String resolveType(String type) {
		if (type == null || type.isEmpty()) {
			return "double";
		}
		for (int hop = 0; hop < TYPEDEF_MAX_HOPS; hop++) {
			String next = typedefs.get(type);
			if (next == null) {
				break;
			}
			type = next;
		}
		return type;
	}

	public Object cast(Object value, String type) {
		String resolved = resolveType(type);
		if (resolved.equalsIgnoreCase("char") || resolved.equalsIgnoreCase("char*")) {
			if (value instanceof String && !((String) value).isEmpty()) {
				return ((String) value).substring(0, 1);
			}
			int code = toInt(value);
			return String.valueOf((char) code);
		}
		if (resolved.equalsIgnoreCase("bool") || resolved.equalsIgnoreCase("boolean")
				|| resolved.equalsIgnoreCase("_bool")) {
			return toBoolean(value) ? 1d : 0d;
		}
		if (resolved.equalsIgnoreCase("int") || resolved.equalsIgnoreCase("int32")
				|| resolved.equalsIgnoreCase("uint32") || resolved.equalsIgnoreCase("long")
				|| resolved.equalsIgnoreCase("int64") || resolved.equalsIgnoreCase("uint64")
				|| resolved.equalsIgnoreCase("short") || resolved.equalsIgnoreCase("int16")
				|| resolved.equalsIgnoreCase("uint16") || resolved.equalsIgnoreCase("byte")
				|| resolved.equalsIgnoreCase("int8") || resolved.equalsIgnoreCase("uint8")) {
			return (double) toInt(value);
		}
		if (resolved.equalsIgnoreCase("float")) {
			return (double) (float) toDouble(value);
		}
		return toDouble(value);
	}

	public int allocationCount() {
		return allocations.size();
	}

	public void reset() {
		allocations.clear();
		typedefs.clear();
		nextAddress = ADDRESS_BASE;
		totalBytes = 0;
	}

	private Allocation findOwner(long address) {
		for (Allocation allocation : allocations.values()) {
			if (address >= allocation.address && address < allocation.address + allocation.data.length) {
				return allocation;
			}
		}
		return null;
	}

	private static int clampSize(int offset, int size, int length) {
		if (offset < 0 || offset >= length) {
			return 0;
		}
		return Math.min(size, length - offset);
	}

	public static int typeSize(String type) {
		if (type == null) {
			return 8;
		}
		switch (type.toLowerCase()) {
			case "char":
			case "char*":
			case "byte":
			case "bool":
			case "boolean":
			case "_bool":
			case "int8":
			case "uint8":
				return 1;
			case "short":
			case "int16":
			case "uint16":
				return 2;
			case "int":
			case "int32":
			case "uint32":
			case "float":
				return 4;
			default:
				return 8;
		}
	}

	private static double toDouble(Object value) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		if (value instanceof String) {
			try {
				return Double.parseDouble(((String) value).trim());
			} catch (NumberFormatException numberFormatException) {
				return 0d;
			}
		}
		if (value instanceof Boolean) {
			return (Boolean) value ? 1d : 0d;
		}
		return 0d;
	}

	private static int toInt(Object value) {
		double d = toDouble(value);
		return (int) (d >= 0 ? Math.floor(d) : Math.ceil(d));
	}

	private static boolean toBoolean(Object value) {
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		if (value instanceof String) {
			String s = ((String) value).trim();
			return !(s.isEmpty() || s.equalsIgnoreCase("false") || s.equalsIgnoreCase("0"));
		}
		return toDouble(value) != 0d;
	}

	private static void encode(byte[] dst, int offset, int size, String type, Object value) {
		if (type.equalsIgnoreCase("double")) {
			long bits = Double.doubleToRawLongBits(toDouble(value));
			storeLE(dst, offset, Math.min(size, 8), bits);
		} else if (type.equalsIgnoreCase("float")) {
			int bits = Float.floatToRawIntBits((float) toDouble(value));
			storeLE(dst, offset, Math.min(size, 4), bits & 0xFFFFFFFFL);
		} else if (type.equalsIgnoreCase("char") || type.equalsIgnoreCase("char*")) {
			if (value instanceof String && !((String) value).isEmpty()) {
				dst[offset] = (byte) ((String) value).charAt(0);
			} else {
				dst[offset] = (byte) toInt(value);
			}
		} else if (type.equalsIgnoreCase("bool") || type.equalsIgnoreCase("boolean")
				|| type.equalsIgnoreCase("_bool")) {
			dst[offset] = toBoolean(value) ? (byte) 1 : (byte) 0;
		} else {
			storeLE(dst, offset, size, toInt(value));
		}
	}

	private static Object decode(byte[] src, int offset, int size, String type) {
		if (type.equalsIgnoreCase("double")) {
			return Double.longBitsToDouble(loadLE(src, offset, Math.min(size, 8)));
		}
		if (type.equalsIgnoreCase("float")) {
			return (double) Float.intBitsToFloat((int) (loadLE(src, offset, Math.min(size, 4)) & 0xFFFFFFFFL));
		}
		if (type.equalsIgnoreCase("char") || type.equalsIgnoreCase("char*")) {
			return String.valueOf((char) (src[offset] & 0xFF));
		}
		if (type.equalsIgnoreCase("bool") || type.equalsIgnoreCase("boolean")
				|| type.equalsIgnoreCase("_bool")) {
			return src[offset] != 0 ? 1d : 0d;
		}
		long raw = loadLE(src, offset, size);
		long extended;
		switch (size) {
			case 1:
				extended = (byte) raw;
				break;
			case 2:
				extended = (short) raw;
				break;
			case 4:
				extended = (int) raw;
				break;
			default:
				extended = raw;
				break;
		}
		return (double) extended;
	}

	private static void storeLE(byte[] dst, int offset, int n, long value) {
		for (int i = 0; i < n; i++) {
			dst[offset + i] = (byte) ((value >> (8 * i)) & 0xFF);
		}
	}

	private static long loadLE(byte[] src, int offset, int n) {
		long value = 0;
		for (int i = 0; i < n; i++) {
			value |= (src[offset + i] & 0xFFL) << (8 * i);
		}
		return value;
	}
}
