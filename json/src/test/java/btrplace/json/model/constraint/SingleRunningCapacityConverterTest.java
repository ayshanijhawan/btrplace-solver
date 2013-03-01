/*
 * Copyright (c) 2012 University of Nice Sophia-Antipolis
 *
 * This file is part of btrplace.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package btrplace.json.model.constraint;

import btrplace.json.JSONConverterException;
import btrplace.json.TestMaterial;
import btrplace.model.constraint.SingleRunningCapacity;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

/**
 * Unit tests for {@link btrplace.model.constraint.SingleRunningCapacity}.
 *
 * @author Fabien Hermenier
 */
public class SingleRunningCapacityConverterTest implements TestMaterial {

    private static SingleRunningCapacityConverter conv = new SingleRunningCapacityConverter();

    @Test
    public void testViables() throws JSONConverterException {
        SingleRunningCapacity d = new SingleRunningCapacity(new HashSet<UUID>(Arrays.asList(n1, n2, n3)), 5, false);
        SingleRunningCapacity c = new SingleRunningCapacity(new HashSet<UUID>(Arrays.asList(n1, n2, n3)), 5, true);
        Assert.assertEquals(conv.fromJSON(conv.toJSON(d)), d);
        Assert.assertEquals(conv.fromJSON(conv.toJSON(c)), c);
    }
}
