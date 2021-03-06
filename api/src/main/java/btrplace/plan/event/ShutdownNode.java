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

package btrplace.plan.event;

import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.model.Node;

import java.util.Objects;

/**
 * An action to shutdown an online node.
 * The node will be in the offline state once the action applied.
 *
 * @author Fabien Hermenier
 */
public class ShutdownNode extends Action implements NodeEvent {

    private Node node;

    /**
     * Create a new shutdown action on an online node.
     *
     * @param n     The node to stop
     * @param start the moment the action starts
     * @param end   the moment the action is finished
     */
    public ShutdownNode(Node n, int start, int end) {
        super(start, end);
        this.node = n;
    }

    @Override
    public Node getNode() {
        return node;
    }


    /**
     * Test the equality with another object.
     *
     * @param o The object to compare with
     * @return true if o is an instance of Shutdown and if both actions act on the same node
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        } else if (o == this) {
            return true;
        } else if (o.getClass() == this.getClass()) {
            ShutdownNode that = (ShutdownNode) o;
            return this.node.equals(that.node) &&
                    this.getStart() == that.getStart() &&
                    this.getEnd() == that.getEnd();

        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStart(), getEnd(), node);
    }

    @Override
    public String pretty() {
        return "shutdown(" + "node=" + node + ")";
    }

    /**
     * Put the node offline on a model
     *
     * @param c the model
     * @return {@code true} if the node was online and is set offline. {@code false} otherwise
     */
    @Override
    public boolean applyAction(Model c) {
        Mapping map = c.getMapping();
        return (!map.isOffline(node) && map.addOfflineNode(node));
    }

    @Override
    public Object visit(ActionVisitor v) {
        return v.visit(this);
    }
}