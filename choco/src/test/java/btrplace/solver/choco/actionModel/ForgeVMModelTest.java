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

package btrplace.solver.choco.actionModel;

import btrplace.model.*;
import btrplace.plan.ReconfigurationPlan;
import btrplace.plan.event.Action;
import btrplace.plan.event.ForgeVM;
import btrplace.plan.event.ShutdownNode;
import btrplace.solver.SolverException;
import btrplace.solver.choco.DefaultReconfigurationProblemBuilder;
import btrplace.solver.choco.ReconfigurationProblem;
import btrplace.solver.choco.durationEvaluator.ConstantActionDuration;
import btrplace.solver.choco.durationEvaluator.DurationEvaluators;
import org.testng.Assert;
import org.testng.annotations.Test;
import solver.Cause;
import solver.exception.ContradictionException;

import java.util.Collections;


/**
 * Unit tests for {@link ForgeVMModel}.
 *
 * @author Fabien Hermenier
 */
public class ForgeVMModelTest {

    @Test
    public void testBasics() throws SolverException {
        Model mo = new DefaultModel();
        Mapping m = mo.getMapping();
        final VM vm1 = mo.newVM();

        mo.getAttributes().put(vm1, "template", "small");
        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ForgeVM.class, new ConstantActionDuration(7));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .setNextVMsStates(Collections.singleton(vm1), Collections.<VM>emptySet(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .build();
        ForgeVMModel ma = (ForgeVMModel) rp.getVMAction(vm1);
        Assert.assertEquals(vm1, ma.getVM());
        Assert.assertEquals(ma.getTemplate(), "small");
        Assert.assertTrue(ma.getDuration().instantiatedTo(7));
        Assert.assertFalse(ma.getStart().instantiated());
        Assert.assertFalse(ma.getEnd().instantiated());
        Assert.assertTrue(ma.getState().instantiatedTo(0));
        Assert.assertNull(ma.getCSlice());
        Assert.assertNull(ma.getDSlice());
    }

    @Test(expectedExceptions = {SolverException.class})
    public void testWithoutTemplate() throws SolverException {
        Model mo = new DefaultModel();
        Mapping m = mo.getMapping();
        final VM vm1 = mo.newVM();

        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ForgeVM.class, new ConstantActionDuration(7));
        new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .setNextVMsStates(Collections.singleton(vm1), Collections.<VM>emptySet(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .build();

    }

    @Test
    public void testResolution() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping m = mo.getMapping();
        final VM vm1 = mo.newVM();
        Node n1 = mo.newNode();

        m.addOnlineNode(n1);
        mo.getAttributes().put(vm1, "template", "small");
        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ForgeVM.class, new ConstantActionDuration(7));
        dev.register(ShutdownNode.class, new ConstantActionDuration(20));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .setNextVMsStates(Collections.singleton(vm1), Collections.<VM>emptySet(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .labelVariables()
                .build();
        //Force the node to get offline
        ShutdownableNodeModel n = (ShutdownableNodeModel) rp.getNodeAction(n1);
        n.getState().instantiateTo(0, Cause.Null);
        System.out.println(rp.getSolver());
        ReconfigurationPlan p = rp.solve(0, false);

        Assert.assertNotNull(p);
        Assert.assertEquals(p.getDuration(), 20);
        for (Action a : p) {
            if (a instanceof ForgeVM) {
                ForgeVM action = (ForgeVM) a;
                Assert.assertTrue(p.getResult().getMapping().isReady(vm1));
                Assert.assertEquals(action.getVM(), vm1);
                Assert.assertEquals(action.getEnd(), 7);
                Assert.assertEquals(action.getStart(), 0);
            }
        }

    }

}
