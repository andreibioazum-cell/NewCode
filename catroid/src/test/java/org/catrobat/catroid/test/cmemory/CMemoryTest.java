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
package org.catrobat.catroid.test.cmemory;

import org.catrobat.catroid.cmemory.CMemory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@RunWith(JUnit4.class)
public class CMemoryTest {

	private CMemory memory;

	@Before
	public void setUp() {
		memory = new CMemory();
	}

	@Test
	public void testMallocReturnsDistinctNonZeroAddresses() {
		long first = memory.malloc(32);
		long second = memory.malloc(32);
		assertTrue(first >= CMemory.ADDRESS_BASE);
		assertTrue(second >= CMemory.ADDRESS_BASE);
		assertTrue(first != second);
		assertEquals(0, memory.malloc(0));
		assertEquals(0, memory.malloc(-4));
	}

	@Test
	public void testCallocIsZeroInitialized() {
		long address = memory.calloc(4, 8);
		assertTrue(address != 0);
		assertEquals(0.0, (Double) memory.pointerGet(address, 0, "double"));
		assertEquals(0.0, (Double) memory.pointerGet(address, 8, "int"));
	}

	@Test
	public void testPointerSetGetDoubleIntCharBool() {
		long address = memory.malloc(64);

		memory.pointerSet(address, 0, 9.5, "double");
		memory.pointerSet(address, 8, 42, "int");
		memory.pointerSet(address, 12, 66, "char");
		memory.pointerSet(address, 13, 7, "bool");

		assertEquals(9.5, (Double) memory.pointerGet(address, 0, "double"));
		assertEquals(42.0, (Double) memory.pointerGet(address, 8, "int"));
		assertEquals("B", memory.pointerGet(address, 12, "char"));
		assertEquals(1.0, (Double) memory.pointerGet(address, 13, "bool"));
	}

	@Test
	public void testTypedefResolvesChainedAliases() {
		memory.defineTypedef("meters", "double");
		memory.defineTypedef("distance", "meters");

		long address = memory.malloc(8);
		memory.pointerSet(address, 0, 3.25, "distance");
		assertEquals(3.25, (Double) memory.pointerGet(address, 0, "meters"));
	}

	@Test
	public void testMemsetAndMemcpy() {
		long target = memory.malloc(8);
		long source = memory.malloc(8);

		memory.pointerSet(source, 0, 3.5, "double");
		memory.memcpy(target, source, 8);
		assertEquals(3.5, (Double) memory.pointerGet(target, 0, "double"));

		memory.memset(target, 1, 4);
		assertEquals(16843009.0, (Double) memory.pointerGet(target, 0, "int"));
	}

	@Test
	public void testReallocPreservesContents() {
		long small = memory.malloc(8);
		memory.pointerSet(small, 0, 42, "int");

		long big = memory.realloc(small, 64);
		assertTrue(big != 0);
		assertTrue(big != small);
		assertEquals(42.0, (Double) memory.pointerGet(big, 0, "int"));
	}

	@Test
	public void testFreeInvalidatesAddress() {
		long address = memory.malloc(8);
		memory.pointerSet(address, 0, 1, "int");
		memory.free(address);
		assertEquals(0.0, (Double) memory.pointerGet(address, 0, "int"));
		memory.free(address);
	}

	@Test
	public void testCastSemantics() {
		assertEquals(3.0, (Double) memory.cast(3.99, "int"));
		assertEquals(-3.0, (Double) memory.cast(-3.99, "int"));
		assertEquals("B", memory.cast(66, "char"));
		assertEquals("A", memory.cast("ABC", "char"));
		assertEquals(1.0, (Double) memory.cast(5, "bool"));
		assertEquals(0.0, (Double) memory.cast(0, "bool"));
		assertEquals(0.0, (Double) memory.cast("false", "bool"));
		double floatPrecision = (Double) memory.cast(1.1, "float");
		assertTrue(Math.abs(floatPrecision - 1.1) > 0);
	}

	@Test
	public void testAccessBeyondAllocationIsClamped() {
		long address = memory.malloc(8);
		memory.pointerSet(address, 4, 1, "int");
		/* only 4 bytes left: the write must be clamped and not throw */
		memory.pointerSet(address, 100, 1, "int");
		assertEquals(1.0, (Double) memory.pointerGet(address, 4, "int"));
	}

	@Test
	public void testClearAllResetsState() {
		long address = memory.malloc(8);
		memory.defineTypedef("alias", "int");
		memory.reset();
		assertEquals(0, memory.allocationCount());
		assertEquals("alias", memory.resolveType("alias"));
	}
}
