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

package btrplace.solver.choco.constraint;

import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.model.Node;
import btrplace.model.VM;
import btrplace.model.constraint.Among;
import btrplace.model.constraint.Fence;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ReconfigurationProblem;
import solver.constraints.Constraint;
import solver.constraints.IntConstraintFactory;
import solver.variables.IntVar;
import solver.variables.VariableFactory;

import java.util.*;

/**
 * Choco implementation of the {@link btrplace.model.constraint.Among} constraint.
 *
 * @author Fabien Hermenier
 */
public class CAmong implements ChocoConstraint {

    private Among cstr;

    private IntVar vmGrpId;

    /**
     * Make a new constraint.
     *
     * @param a the constraint to rely on
     */
    public CAmong(Among a) {
        cstr = a;
    }

    /**
     * Get the group variable that indicate on which group the VMs are running.
     *
     * @return a variable that may be instantiated but {@code null} until
     * {@link #inject(btrplace.solver.choco.ReconfigurationProblem)} has been called
     */
    public IntVar getGroupVariable() {
        return vmGrpId;
    }

    @Override
    public boolean inject(ReconfigurationProblem rp) throws SolverException {

        int nextGrp = -1;
        int curGrp = -1;

        List<Collection<Node>> groups = new ArrayList<>();
        groups.addAll(cstr.getGroupsOfNodes());

        //Browse every VM, check if one is already placed and isolate the future running VMs
        Set<VM> running = new HashSet<>();
        Mapping src = rp.getSourceModel().getMapping();
        for (VM vm : cstr.getInvolvedVMs()) {
            if (rp.getFutureRunningVMs().contains(vm)) {
                //The VM will be running
                running.add(vm);
                IntVar vAssign = rp.getVMAction(vm).getDSlice().getHoster();
                //If one of the VM is already placed, no need for the constraint, the group will be known
                if (vAssign.instantiated()) {
                    //Get the group of nodes that match the selected node
                    int g = getGroup(rp.getNode(vAssign.getValue()));
                    if (g == -1) {
                        rp.getLogger().error("The VM in '{}' will be placed out of any of the allowed group", vm);
                        return false;
                    } else if (nextGrp == -1) {
                        nextGrp = g;
                    } else if (nextGrp != g) {
                        rp.getLogger().error("The VMs in '{}' cannot be spread over multiple group of nodes", cstr.getInvolvedVMs());
                        return false;
                    }
                }
            }

            if (src.isRunning(vm) && cstr.isContinuous()) {
                //The VM is already running, so we get its current group
                Node curNode = src.getVMLocation(vm);
                int g = getGroup(curNode);
                if (curGrp == -1) {
                    curGrp = g;
                } else if (curGrp != g) {
                    rp.getLogger().error("The VMs in '{}' are already spread over multiple group of nodes", cstr.getInvolvedVMs());
                    return false;
                }
            }
        }

        if (cstr.isContinuous() && curGrp != -1) {
            vmGrpId = VariableFactory.fixed(rp.makeVarLabel("among#pGrp"), curGrp, rp.getSolver());
            for (VM v : running) {
                if (!new CFence(new Fence(v, groups.get(curGrp))).inject(rp)) {
                    return false;
                }
            }
        } else {
            if (groups.size() == 1 && !groups.iterator().next().equals(rp.getSourceModel().getMapping().getAllNodes())) {
                //Only 1 group of nodes, it's just a fence constraint
                for (VM v : running) {
                    if (!new CFence(new Fence(v, groups.get(0))).inject(rp)) {
                        return false;
                    }
                }
                vmGrpId = VariableFactory.fixed(rp.makeVarLabel("among#pGrp"), 0, rp.getSolver());
            } else {
                //Now, we create a variable to indicate on which group of nodes the VMs will be
                if (nextGrp == -1) {
                    vmGrpId = VariableFactory.enumerated(rp.makeVarLabel("among#pGrp"), 0, groups.size() - 1, rp.getSolver());
                    //grp: A table to indicate the group each node belong to, -1 for no group
                    int[] grp = new int[rp.getNodes().length];
                    Set<Node> possibleNodes = new HashSet<>();
                    for (int i = 0; i < grp.length; i++) {
                        Node n = rp.getNodes()[i];
                        int idx = getGroup(n);
                        if (idx >= 0) {
                            grp[i] = idx;
                            possibleNodes.add(n);
                        }
                    }
                    //In any case, the VMs cannot go to nodes that are in no groups
                    Collection<Node> ok = new HashSet<>(possibleNodes);
                    for (VM v : running) {
                        if (!new CFence(new Fence(v, ok)).inject(rp)) {
                            return false;
                        }
                    }
                    //We link the VM placement variable with the group variable
                    for (VM vm : running) {
                        IntVar assign = rp.getVMAction(vm).getDSlice().getHoster();
                        Constraint c = IntConstraintFactory.element(assign, grp, vmGrpId, 0, "detect");
                        rp.getSolver().post(c);
                    }
                } else {
                    vmGrpId = VariableFactory.fixed(rp.makeVarLabel("among#pGrp"), nextGrp, rp.getSolver());
                    //As the group is already known, it's now just a fence constraint
                    for (VM v : running) {
                        if (!new CFence(new Fence(v, groups.get(nextGrp))).inject(rp)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Get the group the node belong to.
     *
     * @param n the node
     * @return the group identifier, {@code -1} if the node does not belong to a group
     */
    public int getGroup(Node n) {
        int i = 0;
        for (Collection<Node> pGrp : cstr.getGroupsOfNodes()) {
            if (pGrp.contains(n)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    @Override
    public Set<VM> getMisPlacedVMs(Model m) {
        if (!cstr.isSatisfied(m)) {
            return new HashSet<>(cstr.getInvolvedVMs());
        }
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return cstr.toString();
    }

    /**
     * Builder associated to the constraint.
     */
    public static class Builder implements ChocoConstraintBuilder {
        @Override
        public Class<? extends btrplace.model.constraint.Constraint> getKey() {
            return Among.class;
        }

        @Override
        public CAmong build(btrplace.model.constraint.Constraint c) {
            return new CAmong((Among) c);
        }
    }
}
