package one.chartsy.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RealVectorTest {

	@Test
	void testJoin() {
		RealVector vec1 = RealVector.fromValues(1, 2, 3);
		RealVector vec2 = RealVector.fromValues(4, 5);
		RealVector vec3 = RealVector.fromValues(6, 7, 8, 9);

		assertEquals(RealVector.fromValues(1, 2, 3, 4, 5), vec1.join(vec2));
		assertEquals(RealVector.fromValues(1, 2, 3, 4, 5, 6, 7, 8, 9), vec1.join(vec2, vec3));
	}
}