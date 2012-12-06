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

package btrplace.solver.choco.actionModel;

import btrplace.plan.Action;
import btrplace.plan.action.ResumeVM;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ActionModel;
import btrplace.solver.choco.ReconfigurationProblem;
import btrplace.solver.choco.Slice;
import btrplace.solver.choco.SliceBuilder;
import choco.cp.solver.CPSolver;
import choco.cp.solver.variables.integer.IntDomainVarAddCste;
import choco.kernel.solver.variables.integer.IntDomainVar;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Model an action that resume a sleeping VM.
 * <p/>
 * TODO: support local/remote resume
 *
 * @author Fabien Hermenier
 */
public class ResumeVMModel implements ActionModel {

    private UUID vm;

    private ReconfigurationProblem rp;

    private IntDomainVar start;

    private IntDomainVar end;

    private IntDomainVar duration;

    private Slice dSlice;

    private IntDomainVar state;

    /**
     * Make a new model.
     *
     * @param rp the RP to use as a basis.
     * @param e  the VM managed by the action
     * @throws SolverException if an error occurred
     */
    public ResumeVMModel(ReconfigurationProblem rp, UUID e) throws SolverException {
        this.rp = rp;
        this.vm = e;

        int d = rp.getDurationEvaluators().evaluate(ResumeVM.class, e);

        start = rp.makeDuration("", 0, rp.getEnd().getSup() - d);
        end = new IntDomainVarAddCste(rp.getSolver(), rp.makeVarLabel("resumeVM(" + e + ").end"), start, d);
        duration = rp.makeDuration(rp.makeVarLabel("resumeVM(" + e + ").duration"), d, d);
        dSlice = new SliceBuilder(rp, e, "resumeVM(" + e + ").dSlice").setStart(start)
                .setDuration(rp.makeDuration(rp.makeVarLabel("resumeVM(" + e + ").dSlice_duration"), d, rp.getEnd().getSup()))
                .setExclusive(false)
                .build();

        CPSolver s = rp.getSolver();
        s.post(s.leq(end, rp.getEnd()));
        state = s.makeConstantIntVar(1);
    }

    @Override
    public List<Action> getResultingActions() {
        List<Action> l = new ArrayList<Action>(1);
        l.add(new ResumeVM(vm, rp.getSourceModel().getMapping().getVMLocation(vm),
                rp.getNode(dSlice.getHoster().getVal()),
                start.getVal(), end.getVal()));
        return l;
    }

    /**
     * Get the VM manipulated by the action.
     *
     * @return the VM identifier
     */
    public UUID getVM() {
        return vm;
    }

    @Override
    public IntDomainVar getStart() {
        return start;
    }

    @Override
    public IntDomainVar getEnd() {
        return end;
    }

    @Override
    public IntDomainVar getDuration() {
        return duration;
    }

    @Override
    public Slice getCSlice() {
        return null;
    }

    @Override
    public Slice getDSlice() {
        return dSlice;
    }

    @Override
    public IntDomainVar getState() {
        return state;
    }

    @Override
    public void visit(ActionModelVisitor v) {
        v.visit(this);
    }

}
