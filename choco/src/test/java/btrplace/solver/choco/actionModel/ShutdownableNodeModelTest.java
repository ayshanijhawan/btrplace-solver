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
import btrplace.plan.event.BootNode;
import btrplace.plan.event.ShutdownNode;
import btrplace.plan.event.ShutdownVM;
import btrplace.solver.SolverException;
import btrplace.solver.choco.DefaultReconfigurationProblemBuilder;
import btrplace.solver.choco.ReconfigurationProblem;
import btrplace.solver.choco.durationEvaluator.ConstantActionDuration;
import btrplace.solver.choco.durationEvaluator.DurationEvaluators;
import org.testng.Assert;
import org.testng.annotations.Test;
import solver.Cause;
import solver.Solver;
import solver.constraints.IntConstraintFactory;
import solver.exception.ContradictionException;

import java.util.Collections;


/**
 * Unit tests for {@link ShutdownableNodeModel}.
 *
 * @author Fabien hermenier
 */
public class ShutdownableNodeModelTest {

    @Test
    public void testBasics() throws SolverException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        Node n1 = mo.newNode();
        map.addOnlineNode(n1);

        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .labelVariables()
                .build();
        ShutdownableNodeModel ma = (ShutdownableNodeModel) rp.getNodeAction(n1);
        Assert.assertEquals(ma.getNode(), n1);
        Assert.assertEquals(ma.getHostingStart(), rp.getStart());
    }

    @Test
    public void testForcedOnline() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOfflineNode(n2);

        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        dev.register(BootNode.class, new ConstantActionDuration(10));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();
        ShutdownableNodeModel ma = (ShutdownableNodeModel) rp.getNodeAction(n1);
        ma.getState().instantiateTo(1, Cause.Null);   //stay online

        //To make the result plan 10 seconds long
        BootableNodeModel ma2 = (BootableNodeModel) rp.getNodeAction(n2);
        ma2.getState().instantiateTo(1, Cause.Null); //go online
        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertEquals(ma.getDuration().getValue(), 0);
        Assert.assertEquals(ma.getStart().getValue(), 0);
        Assert.assertEquals(ma.getEnd().getValue(), 0);
        Assert.assertEquals(ma.getHostingStart().getValue(), 0);
        Assert.assertEquals(ma.getHostingEnd().getValue(), 10);


        Assert.assertEquals(p.getSize(), 1);
        Model res = p.getResult();
        Assert.assertTrue(res.getMapping().isOnline(n1));
    }

    @Test
    public void testForcedOffline() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        Node n1 = mo.newNode();

        map.addOnlineNode(n1);

        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();
        ShutdownableNodeModel ma = (ShutdownableNodeModel) rp.getNodeAction(n1);
        ma.getState().instantiateTo(0, Cause.Null);

        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        Assert.assertEquals(ma.getDuration().getValue(), 5);
        Assert.assertEquals(ma.getStart().getValue(), 0);
        Assert.assertEquals(ma.getEnd().getValue(), 5);
        Assert.assertEquals(ma.getHostingStart().getValue(), 0);
        Assert.assertEquals(ma.getHostingEnd().getValue(), 0);

        Assert.assertEquals(p.getSize(), 1);
        Model res = p.getResult();
        Assert.assertTrue(res.getMapping().getOfflineNodes().contains(n1));
    }

    @Test
    public void testScheduledShutdown() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        final VM vm1 = mo.newVM();
        Node n1 = mo.newNode();

        map.addOnlineNode(n1);
        map.addRunningVM(vm1, n1);
        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownVM.class, new ConstantActionDuration(2));
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .labelVariables()
                .setNextVMsStates(Collections.singleton(vm1), Collections.<VM>emptySet(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .build();
        ShutdownableNodeModel ma = (ShutdownableNodeModel) rp.getNodeAction(n1);
        ma.getState().instantiateTo(0, Cause.Null);

        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        Assert.assertEquals(ma.getState().getValue(), 0);
        Assert.assertEquals(ma.getDuration().getValue(), 5);
        Assert.assertEquals(ma.getStart().getValue(), 2);
        Assert.assertEquals(ma.getEnd().getValue(), 7);
        Assert.assertEquals(ma.getHostingStart().getValue(), 0);
        Assert.assertEquals(ma.getHostingEnd().getValue(), 2);


        Model res = p.getResult();
        Assert.assertTrue(res.getMapping().getOfflineNodes().contains(n1));
    }

    /**
     * The 2 nodes are set offline but n2 will consume being offline after n1
     *
     * @throws SolverException
     * @throws ContradictionException
     */
    @Test
    public void testCascadedShutdown() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);

        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownVM.class, new ConstantActionDuration(2));
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();
        ShutdownableNodeModel ma1 = (ShutdownableNodeModel) rp.getNodeAction(n1);
        ShutdownableNodeModel ma2 = (ShutdownableNodeModel) rp.getNodeAction(n2);
        ma1.getState().instantiateTo(0, Cause.Null);
        ma2.getState().instantiateTo(0, Cause.Null);

        Solver solver = rp.getSolver();
        solver.post(IntConstraintFactory.arithm(ma2.getStart(), "=", ma1.getEnd()));

        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertEquals(ma1.getStart().getValue(), 0);
        Assert.assertEquals(ma2.getStart().getValue(), ma1.getEnd().getValue());
        Model res = p.getResult();
        Assert.assertEquals(res.getMapping().getOfflineNodes().size(), 2);
    }

    @Test
    public void testShutdownBeforeVMsLeave() throws SolverException, ContradictionException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        final VM vm1 = mo.newVM();
        Node n1 = mo.newNode();

        map.addOnlineNode(n1);
        map.addRunningVM(vm1, n1);
        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownVM.class, new ConstantActionDuration(2));
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setNextVMsStates(Collections.singleton(vm1), Collections.<VM>emptySet(), Collections.<VM>emptySet(), Collections.<VM>emptySet())
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();
        ShutdownableNodeModel ma1 = (ShutdownableNodeModel) rp.getNodeAction(n1);
        ma1.getState().instantiateTo(0, Cause.Null);
        ma1.getHostingEnd().instantiateTo(0, Cause.Null);
        rp.getEnd().updateUpperBound(10, Cause.Null);
        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNull(p);
        System.out.println(p);
    }

    @Test
    public void testSwitchState() throws ContradictionException, SolverException {
        Model mo = new DefaultModel();
        Mapping map = mo.getMapping();
        Node n1 = mo.newNode();
        Node n2 = mo.newNode();

        map.addOnlineNode(n1);
        map.addOfflineNode(n2);
        DurationEvaluators dev = new DurationEvaluators();
        dev.register(BootNode.class, new ConstantActionDuration(2));
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(mo)
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();
        ShutdownableNodeModel ma1 = (ShutdownableNodeModel) rp.getNodeAction(n1);
        BootableNodeModel ma2 = (BootableNodeModel) rp.getNodeAction(n2);
        ma1.getState().instantiateTo(0, Cause.Null);
        ma2.getState().instantiateTo(1, Cause.Null);
        Solver solver = rp.getSolver();
        solver.post(IntConstraintFactory.arithm(ma1.getEnd(), "=", ma2.getStart()));
        ReconfigurationPlan p = rp.solve(0, false);
        //ChocoLogging.flushLogs();
        Assert.assertNotNull(p);
        System.out.println(p);
        System.out.flush();
    }

    /**
     * Issue #2 about NodeActionModel.
     *
     * @throws SolverException
     * @throws ContradictionException
     */
    @Test
    public void testNodeHostingEnd() throws SolverException, ContradictionException {
        Model model = new DefaultModel();
        Mapping map = model.getMapping();
        Node n1 = model.newNode();
        Node n2 = model.newNode();
        Node n3 = model.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n2);
        map.addOfflineNode(n3);
        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        dev.register(BootNode.class, new ConstantActionDuration(10));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(model)
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();
        ShutdownableNodeModel shd = (ShutdownableNodeModel) rp.getNodeAction(n1);
        shd.getState().instantiateTo(1, Cause.Null); //Stay online

        ShutdownableNodeModel shd2 = (ShutdownableNodeModel) rp.getNodeAction(n2);
        shd2.getState().instantiateTo(0, Cause.Null);  //Go offline
        shd2.getStart().instantiateTo(1, Cause.Null); //Start going offline at 1

        BootableNodeModel bn = (BootableNodeModel) rp.getNodeAction(n3);
        bn.getState().instantiateTo(1, Cause.Null); //Go online
        bn.getStart().instantiateTo(6, Cause.Null); //Start going online at 6
        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertEquals(shd.getDuration().getValue(), 0);
        Assert.assertEquals(shd.getStart().getValue(), 0);
        Assert.assertEquals(shd.getEnd().getValue(), 0);
        Assert.assertEquals(shd.getHostingStart().getValue(), 0);
        Assert.assertEquals(shd.getHostingEnd().getValue(), 16);
        Assert.assertEquals(shd2.getDuration().getValue(), 5);
        Assert.assertEquals(shd2.getStart().getValue(), 1);
        Assert.assertEquals(shd2.getEnd().getValue(), 6);
        Assert.assertEquals(shd2.getHostingStart().getValue(), 0);
        Assert.assertEquals(shd2.getHostingEnd().getValue(), 1);
        Assert.assertEquals(bn.getStart().getValue(), 6);
        Assert.assertEquals(bn.getDuration().getValue(), 10);
        Assert.assertEquals(bn.getEnd().getValue(), 16);
        Assert.assertEquals(bn.getHostingStart().getValue(), 16);
        Assert.assertEquals(bn.getHostingEnd().getValue(), 16);
        Assert.assertEquals(p.getSize(), 2);
        Model res = p.getResult();
        Assert.assertTrue(res.getMapping().isOnline(n1));
        Assert.assertTrue(res.getMapping().isOnline(n3));
        Assert.assertTrue(res.getMapping().getOfflineNodes().contains(n2));
    }

    @Test
    public void testActionDurationSimple() throws SolverException, ContradictionException {
        Model model = new DefaultModel();
        Mapping map = model.getMapping();

        Node n1 = model.newNode();
        Node n4 = model.newNode();

        map.addOnlineNode(n1);
        map.addOfflineNode(n4);

        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        dev.register(BootNode.class, new ConstantActionDuration(3));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(model)
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();

        ShutdownableNodeModel sn1 = (ShutdownableNodeModel) rp.getNodeAction(n1);
        sn1.getState().instantiateTo(0, Cause.Null);
        BootableNodeModel bn4 = (BootableNodeModel) rp.getNodeAction(n4);
        bn4.getState().instantiateTo(0, Cause.Null);

        ReconfigurationPlan p = rp.solve(0, false);
        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertEquals(rp.getStart().getValue(), 0);
        Assert.assertEquals(bn4.getStart().getValue(), 0);
        Assert.assertEquals(bn4.getDuration().getValue(), 0);
        Assert.assertEquals(bn4.getEnd().getValue(), 0);
        Assert.assertEquals(bn4.getHostingStart().getValue(), 0);
        Assert.assertEquals(bn4.getHostingEnd().getValue(), 0);
        Assert.assertEquals(p.getSize(), 1);
        Model res = p.getResult();
        Assert.assertTrue(res.getMapping().getOfflineNodes().contains(n1));
        Assert.assertTrue(res.getMapping().getOfflineNodes().contains(n4));
    }

    @Test
    public void testShutdownable() throws SolverException, ContradictionException {
        Model model = new DefaultModel();
        Mapping map = model.getMapping();

        Node n1 = model.newNode();
        Node n4 = model.newNode();

        map.addOnlineNode(n1);
        map.addOnlineNode(n4);
        DurationEvaluators dev = new DurationEvaluators();
        dev.register(ShutdownNode.class, new ConstantActionDuration(5));
        dev.register(BootNode.class, new ConstantActionDuration(3));
        ReconfigurationProblem rp = new DefaultReconfigurationProblemBuilder(model)
                .setDurationEvaluators(dev)
                .labelVariables()
                .build();

        ShutdownableNodeModel sn1 = (ShutdownableNodeModel) rp.getNodeAction(n1);
        sn1.getState().instantiateTo(0, Cause.Null);
        sn1.getStart().instantiateTo(2, Cause.Null);
        ShutdownableNodeModel sn4 = (ShutdownableNodeModel) rp.getNodeAction(n4);
        sn4.getState().instantiateTo(1, Cause.Null);

        ReconfigurationPlan p = rp.solve(0, false);

        Assert.assertNotNull(p);
        System.out.println(p);
        Assert.assertEquals(rp.getStart().getValue(), 0);
        Assert.assertEquals(rp.getEnd().getValue(), 7);

        Assert.assertEquals(sn1.getStart().getValue(), 2);
        Assert.assertEquals(sn1.getDuration().getValue(), 5);
        Assert.assertEquals(sn1.getEnd().getValue(), 7);
        Assert.assertEquals(sn1.getHostingStart().getValue(), 0);
        Assert.assertEquals(sn1.getHostingEnd().getValue(), 2);

        Assert.assertEquals(rp.getStart().getValue(), 0);
        Assert.assertEquals(sn4.getStart().getValue(), 0);
        Assert.assertEquals(sn4.getDuration().getValue(), 0);
        Assert.assertEquals(sn4.getEnd().getValue(), 0);
        Assert.assertEquals(sn4.getHostingStart().getValue(), 0);
        Assert.assertEquals(sn4.getHostingEnd().getValue(), 7);
        Assert.assertEquals(p.getSize(), 1);
        Model res = p.getResult();
        Assert.assertTrue(res.getMapping().isOnline(n4));
        Assert.assertTrue(res.getMapping().getOfflineNodes().contains(n1));
    }
}
