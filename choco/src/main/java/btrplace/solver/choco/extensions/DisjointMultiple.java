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

package btrplace.solver.choco.extensions;


import memory.IStateBitSet;
import memory.IStateInt;
import solver.Solver;
import solver.constraints.IntConstraint;
import solver.constraints.Propagator;
import solver.constraints.PropagatorPriority;
import solver.exception.ContradictionException;
import solver.variables.EventType;
import solver.variables.IntVar;
import solver.variables.delta.IIntDeltaMonitor;
import util.ESat;
import util.iterators.DisposableValueIterator;
import util.procedure.UnaryIntProcedure;
import util.tools.ArrayUtils;

import java.util.BitSet;

/**
 * Enforces multiple sets of variables values to be disjoint
 * created sofdem - 08/09/11
 *
 * @author Sophie Demassey
 */
public class DisjointMultiple extends IntConstraint<IntVar> {

    /**
     * the variable domains must be included in [0, nbValues-1]
     */
    private final int nbValues;

    /**
     * the number of groups
     */
    private final int nbGroups;

    /**
     * indices of variables in group 'g' is between groupIdx[g] and groupIdx[g+1]
     * with 0 <= g < nbGroups
     */
    private final int[] groupIdx;

    /**
     * candidates[g][v] = number of variables in group 'g' which can be assigned to value 'v',
     * with 0 <= g < nbGroups and 0 <= v < nbValues
     */
    private IStateInt[][] candidates;
    /**
     * required[g].get(v) iff at least one variable in group 'g' is assigned to value 'v',
     * with 0 <= g < nbGroups and 0 <= v < nbValues
     */
    private IStateBitSet[] required;

    /**
     * @param s  solver
     * @param vs sets of variables
     * @param c  max variable value + 1
     */
    public DisjointMultiple(Solver s, IntVar[][] vs, int c) {
        super(ArrayUtils.flatten(vs), s);
        nbValues = c;
        nbGroups = vs.length;
        groupIdx = new int[nbGroups + 1];
        candidates = new IStateInt[nbGroups][c];
        required = new IStateBitSet[nbGroups];
        groupIdx[0] = 0;
        int idx = 0;
        for (int g = 0; g < nbGroups; g++) {
            idx += vs[g].length;
            groupIdx[g + 1] = idx;
            required[g] = s.getEnvironment().makeBitSet(c);
            for (int v = 0; v < c; v++) {
                candidates[g][v] = s.getEnvironment().makeInt(0);
            }
        }
        setPropagators(new DisjointPropagator(vs));
    }

    private int getGroup(int idx) {
        return getGroup(idx, 0, nbGroups);
    }

    private int getGroup(int idx, int s, int e) {
        assert e > s && groupIdx[s] <= idx && idx < groupIdx[e];
        if (e == s + 1) {
            return s;
        }
        int m = (s + e) / 2;
        if (idx >= groupIdx[m]) {
            return getGroup(idx, m, e);
        }
        return getGroup(idx, s, m);

    }

    @Override
    public ESat isSatisfied(int[] tuple) {
        BitSet valuesOne = new BitSet(nbValues);
        for (int g = 0; g < nbGroups; g++) {
            for (int i = groupIdx[g]; i < groupIdx[g + 1]; i++) {
                valuesOne.set(tuple[i]);
            }
            for (int i = 0; i < groupIdx[g]; i++) {
                if (valuesOne.get(tuple[i])) {
                    return ESat.FALSE;
                }
            }
            for (int i = groupIdx[g + 1]; i < groupIdx[nbGroups + 1]; i++) {
                if (valuesOne.get(tuple[i])) {
                    return ESat.FALSE;
                }
            }
            valuesOne.clear();
        }
        return ESat.TRUE;
    }

    class DisjointPropagator extends Propagator<IntVar> {

        private IIntDeltaMonitor[] idms;

        private boolean first = true;

        private RemProc remProc;

        public DisjointPropagator(IntVar[][] g) {
            super(ArrayUtils.flatten(g), PropagatorPriority.VERY_SLOW, true);
            idms = new IIntDeltaMonitor[vars.length];
            int i = 0;
            for (IntVar v : vars) {
                idms[i++] = v.monitorDelta(aCause);
            }
            remProc = new RemProc();
        }

        @Override
        protected int getPropagationConditions(int vIdx) {
            return EventType.REMOVE.mask + EventType.INSTANTIATE.mask;
        }

        @Override
        public ESat isEntailed() {
            BitSet valuesOne = new BitSet(nbValues);
            for (int g = 0; g < nbGroups; g++) {
                for (int i = groupIdx[g]; i < groupIdx[g + 1]; i++) {
                    valuesOne.set(vars[i].getValue());
                }
                for (int i = 0; i < groupIdx[g]; i++) {
                    if (valuesOne.get(vars[i].getValue())) {
                        return ESat.FALSE;
                    }
                }
                for (int i = groupIdx[g + 1]; i < groupIdx[nbGroups]; i++) {
                    if (valuesOne.get(vars[i].getValue())) {
                        return ESat.FALSE;
                    }
                }
                valuesOne.clear();
            }
            return ESat.TRUE;
        }

        /**
         * Initialise required and candidate for a given variable that belong to a given group.
         *
         * @param var   the variable
         * @param group the group of the variable
         */
        private void initVar(IntVar var, int group) {
            if (var.instantiated()) {
                required[group].set(var.getValue());
            } else {
                DisposableValueIterator it = var.getValueIterator(true);
                try {
                    while (it.hasNext()) {
                        int val = it.next();
                        candidates[group][val].add(1);
                    }
                } finally {
                    it.dispose();
                }
            }
        }

        @Override
        public void propagate(int m) throws ContradictionException {
            if (first) {
                first = false;
                int i = 0;
                for (int g = 0; g < nbGroups; g++) {
                    for (; i < groupIdx[g + 1]; i++) {
                        initVar(vars[i], g);
                    }
                }
            }

            for (int v = 0; v < nbValues; v++) {
                for (int g = 0; g < nbGroups; g++) {
                    if (required[g].get(v)) {
                        setRequired(v, g);
                    }
                }
            }
        }

        @Override
        public void propagate(int idx, int mask) throws ContradictionException {
            if (EventType.isRemove(mask)) {
                idms[idx].freeze();
                idms[idx].forEach(remProc.set(idx), EventType.REMOVE);
                idms[idx].unfreeze();
            }
            if (EventType.isInstantiate(mask)) {
                int group = getGroup(idx);
                if (!required[group].get(vars[idx].getValue())) {
                    setRequired(vars[idx].getValue(), group);
                }
            }
        }

        /**
         * update the internal data and filter when a variable is newly instantiated
         * 1) fail if a variable in the other group is already instantiated to this value
         * 2) remove the value of the domains of all the variables of the other group
         *
         * @param val   the new assigned value
         * @param group the group of the new instantiated variable
         * @throws ContradictionException when some variables in both groups are instantiated to the same value
         */
        public void setRequired(int val, int group) throws ContradictionException {
            required[group].set(val);
            for (int g = 0; g < nbGroups; g++) {
                if (g != group) {
                    if (required[g].get(val)) {
                        //The value is used in the other group. It's a contradiction
                        contradiction(null, "");
                    }
                    if (candidates[g][val].get() > 0) {
                        //The value was possible for the other group, so we remove it from its variable
                        for (int i = groupIdx[g]; i < groupIdx[g + 1]; i++) {
                            if (vars[i].removeValue(val, aCause)) {
                                candidates[g][val].add(-1);
                                if (vars[i].instantiated()) {
                                    if (!required[g].get(vars[i].getValue())) {
                                        setRequired(vars[i].getValue(), g);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private class RemProc implements UnaryIntProcedure<Integer> {
        private int group;

        @Override
        public UnaryIntProcedure set(Integer idxVar) {
            this.group = getGroup(idxVar);
            return this;
        }

        @Override
        public void execute(int val) throws ContradictionException {
            candidates[group][val].add(-1);
        }
    }
}
