/*
 * Copyright (c) 2013 University of Nice Sophia-Antipolis
 *
 * This file is part of btrplace.
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

package btrplace.solver.choco;

import btrplace.model.DefaultModel;
import btrplace.model.Model;
import btrplace.model.VM;
import org.testng.Assert;
import org.testng.annotations.Test;
import solver.Solver;
import solver.variables.IntVar;
import solver.variables.VF;

/**
 * Unit tests for {@link Slice}.
 *
 * @author Fabien Hermenier
 */
public class SliceTest {

    /**
     * It's a container so we only tests the instantiation and the getters.
     */
    @Test
    public void testInstantiation() {
        Model mo = new DefaultModel();
        VM vm1 = mo.newVM();
        Solver s = new Solver();
        IntVar st = VF.fixed("start", 1, s);
        IntVar ed = VF.fixed("end", 3, s);
        IntVar duration = VF.fixed("duration", 2, s);
        IntVar hoster = VF.fixed("hoster", 4, s);
        Slice sl = new Slice(vm1, st, ed, duration, hoster);
        Assert.assertEquals(vm1, sl.getSubject());
        Assert.assertEquals(st, sl.getStart());
        Assert.assertEquals(ed, sl.getEnd());
        Assert.assertEquals(hoster, sl.getHoster());
        Assert.assertEquals(duration, sl.getDuration());
        Assert.assertFalse(sl.toString().contains("null"));
        duration = VF.bounded("duration", 3, 5, s);
        sl = new Slice(vm1, st, ed, duration, hoster);
        Assert.assertFalse(sl.toString().contains("null"));
    }
}
