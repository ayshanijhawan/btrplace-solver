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

package btrplace.json.model.view;

import btrplace.json.JSONConverterException;
import btrplace.model.Element;
import btrplace.model.view.NamingService;
import net.minidev.json.JSONObject;

import java.util.Map;


/**
 * Serialize/Un-serialize an {@link btrplace.model.view.NamingService}.
 *
 * @author Fabien Hermenier
 */
public class NamingServiceConverter extends ModelViewConverter<NamingService> {

    @Override
    public Class<NamingService> getSupportedView() {
        return NamingService.class;
    }

    @Override
    public String getJSONId() {
        return "ns";
    }

    @Override
    public JSONObject toJSON(NamingService rc) {
        JSONObject container = new JSONObject();
        container.put("id", getJSONId());
        container.put("type", rc.getElementIdentifier());
        JSONObject map = new JSONObject();
        for (Object o : rc.getNamedElements()) {
            Element e = (Element) o;
            map.put(rc.resolve(e), e.id());
        }
        container.put("map", map);
        return container;
    }

    @Override
    public NamingService fromJSON(JSONObject o) throws JSONConverterException {
        if (!o.containsKey("id") || !o.containsKey("type") || !o.containsKey("map")) {
            return null;
        }
        String id = o.get("id").toString();
        if (!id.equals(getJSONId())) {
            return null;
        }

        NamingService ns = null;
        String type = o.get("type").toString();
        switch (type) {
            case "vm":
                ns = NamingService.newVMNS();
                break;
            case "node":
                ns = NamingService.newNodeNS();
                break;
            default:
                throw new JSONConverterException("Unsupported type of element '" + type + "'");
        }

        JSONObject map = (JSONObject) o.get("map");
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String n = e.getKey();
            int v = Integer.parseInt(e.getValue().toString());
            Element el = type.equals("vm") ? getOrMakeVM(v) : getOrMakeNode(v);
            if (!ns.register(el, n)) {
                throw new JSONConverterException("Duplicated name '" + n + "'");
            }
        }
        return ns;
    }
}
