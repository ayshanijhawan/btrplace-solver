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

package btrplace.model.constraint.checker;

import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.model.Node;
import btrplace.model.VM;
import btrplace.model.constraint.Spread;
import btrplace.plan.event.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Checker for the {@link btrplace.model.constraint.Spread} constraint
 *
 * @author Fabien Hermenier
 * @see btrplace.model.constraint.Ban
 */
public class SpreadChecker extends AllowAllConstraintChecker<Spread> {

    /**
     * Make a new checker.
     *
     * @param s the associated constraint
     */
    public SpreadChecker(Spread s) {
        super(s);
        denied = new HashSet<>();
    }

    private Set<Node> denied;

    @Override
    public boolean startsWith(Model mo) {
        if (getConstraint().isContinuous()) {
            Mapping map = mo.getMapping();
            for (VM vm : getVMs()) {
                Node n = map.getVMLocation(vm);
                if (n != null) {
                    denied.add(n);
                }
            }
        }
        return true;
    }

    @Override
    public boolean startRunningVMPlacement(RunningVMPlacement a) {
        if (getConstraint().isContinuous() && getVMs().contains(a.getVM())) {
            if (denied.contains(a.getDestinationNode())) {
                return false;
            }
            denied.add(a.getDestinationNode());
        }
        return true;
    }

    @Override
    public void end(MigrateVM a) {
        unDenied(a.getVM(), a.getSourceNode());
    }

    private void unDenied(VM vm, Node n) {
        if (getConstraint().isContinuous() && getVMs().contains(vm)) {
            denied.remove(n);
        }
    }

    @Override
    public void end(ShutdownVM a) {
        unDenied(a.getVM(), a.getNode());
    }

    @Override
    public void end(SuspendVM a) {
        unDenied(a.getVM(), a.getSourceNode());
    }

    @Override
    public void end(KillVM a) {
        unDenied(a.getVM(), a.getNode());
    }

    @Override
    public boolean endsWith(Model mo) {
        Set<Node> forbidden = new HashSet<>();
        Mapping map = mo.getMapping();
        for (VM vm : getVMs()) {
            if (map.isRunning(vm)) {
                if (!forbidden.add(map.getVMLocation(vm))) {
                    return false;
                }
            }
        }
        return true;
    }
}
