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

package btrplace.model.constraint;

import btrplace.model.Node;
import btrplace.model.VM;
import btrplace.model.constraint.checker.OfflineChecker;
import btrplace.model.constraint.checker.SatConstraintChecker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A constraint to force a node at being offline.
 * <p/>
 * The restriction provided by the constraint is discrete.
 * however, if the node are already offline, then
 * its state will be unchanged.
 *
 * @author Fabien Hermenier
 */
public class Offline extends SatConstraint {

    /**
     * Instantiate constraints for a collection of nodes.
     *
     * @param nodes the nodes to integrate
     * @return the associated list of constraints
     */
    public static List<Offline> newOffline(Collection<Node> nodes) {
        List<Offline> l = new ArrayList<>(nodes.size());
        for (Node n : nodes) {
            l.add(new Offline(n));
        }
        return l;
    }

    /**
     * Make a new constraint.
     *
     * @param n the node to set offline
     */
    public Offline(Node n) {
        super(Collections.<VM>emptyList(), Collections.singleton(n), false);
    }

    @Override
    public SatConstraintChecker getChecker() {
        return new OfflineChecker(this);
    }

    @Override
    public boolean setContinuous(boolean b) {
        return !b;
    }

    @Override
    public String toString() {
        return "offline(nodes=" + getInvolvedNodes().iterator().next() + ", discrete)";
    }
}
