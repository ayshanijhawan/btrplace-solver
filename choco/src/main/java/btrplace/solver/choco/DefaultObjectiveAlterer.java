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

import solver.ResolutionPolicy;

/**
 * Default objective alterer.
 * The new bound is the current computed value plus or minus 1 depending
 * on the resolution policy.
 *
 * @author Fabien Hermenier
 */
public class DefaultObjectiveAlterer implements ObjectiveAlterer {

    @Override
    public int newBound(ReconfigurationProblem rp, int currentValue) {
        if (rp.getResolutionPolicy() == ResolutionPolicy.MAXIMIZE) {
            return currentValue + 1;
        }
        return currentValue - 1;
    }
}
