package btrplace.examples;

import btrplace.model.DefaultMapping;
import btrplace.model.DefaultModel;
import btrplace.model.Mapping;
import btrplace.model.Model;
import btrplace.model.constraint.Overbook;
import btrplace.model.constraint.Preserve;
import btrplace.model.constraint.SatConstraint;
import btrplace.model.view.ShareableResource;
import btrplace.plan.ReconfigurationPlan;
import btrplace.plan.event.MigrateVM;
import btrplace.solver.SolverException;
import btrplace.solver.choco.ChocoReconfigurationAlgorithm;
import btrplace.solver.choco.DefaultChocoReconfigurationAlgorithm;
import btrplace.solver.choco.durationEvaluator.DurationEvaluator;
import btrplace.solver.choco.durationEvaluator.DurationEvaluators;

import java.util.*;

/**
 * Tutorial about the basic tuning of a {@link btrplace.solver.choco.ChocoReconfigurationAlgorithm}.
 * The document associated to the tutorial is available
 * on <a href="https://github.com/fhermeni/btrplace-solver/wiki/Basic-Tuning">btrplace website</a>.
 *
 * @author Fabien Hermenier
 */
public class BasicTuning implements Example {

    private List<UUID> nodes;

    private ShareableResource rcBW;

    private ShareableResource rcMem;

    @Override
    public String toString() {
        return "Basic Tuning";
    }

    @Override
    public boolean run() {

        //Make a default model with 500 nodes hosting 3,000 VMs
        Model model = makeModel();

        Set<SatConstraint> constraints = new HashSet<>();

        //On 10 nodes, the VMs ask now for more bandwidth
        constraints.addAll(getNewBWRequirements(model));

        //We allow memory over-commitment with a overbooking ratio of 50%
        //i.e. 1MB physical RAM for 1.5MB virtual RAM
        constraints.add(new Overbook(model.getMapping().getAllNodes(), "mem", 1.5));

        ChocoReconfigurationAlgorithm cra = new DefaultChocoReconfigurationAlgorithm();

        //Customize the estimated duration of actions
        customizeMigrationDuration(cra);

        //We want the best possible solution, computed in up to 5 sec.
        cra.doOptimize(true);
        cra.setTimeLimit(5);

        //We solve without the repair mode
        cra.doRepair(false);
        solve(cra, model, constraints);

        //Re-solve using the repair mode to check for the improvement
        cra.doRepair(true);
        solve(cra, model, constraints);
        return true;
    }

    /**
     * We customize the estimate duration of the VM migration action
     * to be equals to 1 second per GB of memory plus 3 seconds
     */
    private void customizeMigrationDuration(ChocoReconfigurationAlgorithm cra) {
        DurationEvaluators devs = cra.getDurationEvaluators();

        DurationEvaluator ev = new DurationEvaluator() {
            @Override
            public int evaluate(UUID e) {
                return rcMem.get(e) + 3;
            }
        };
        //Associate the evaluator to an action.
        devs.register(MigrateVM.class, ev);
    }

    private void solve(ChocoReconfigurationAlgorithm cra, Model model, Set<SatConstraint> constraints) {
        try {
            ReconfigurationPlan p = cra.solve(model, constraints);
            if (p != null) {
                System.out.println("--- Solving using repair : " + cra.doRepair());
                System.out.println(cra.getSolvingStatistics());
            }
        } catch (SolverException e) {
            System.err.println("--- Solving using repair : " + cra.doRepair() + "; Error: " + e.getMessage());
        }
    }

    /**
     * A default model with 1000 nodes hosting 6,000 VMs.
     * 6 VMs per node
     * Each node has a 10GB network interface
     * Each VM consumes 2GB
     */
    private Model makeModel() {
        Mapping mapping = new DefaultMapping();


        //Memory usage/consumption in GB
        rcMem = new ShareableResource("mem");

        //A resource representing the bandwidth usage/consumption of the elements in GB
        rcBW = new ShareableResource("bandwidth");


        nodes = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            UUID n = new UUID(1, i);
            nodes.add(n);
            mapping.addOnlineNode(n);

            //Each node provides a 10GB bandwidth and 32 GB RAM to its VMs
            rcBW.set(n, 10);
            rcMem.set(n, 32);
        }

        for (int i = 0; i < 600; i++) {
            UUID vm = new UUID(0, i);
            //Basic balancing through a round-robin: 6 VMs per node
            mapping.addRunningVM(vm, nodes.get(i % nodes.size()));

            //Each VM uses currently a 1GB bandwidth and 1,2 or 3 GB RAM
            rcBW.set(vm, 1);
            rcMem.set(vm, i % 5 + 1);
        }

        Model mo = new DefaultModel(mapping);
        mo.attach(rcBW);
        mo.attach(rcMem);
        return mo;
    }

    /**
     * On 10 nodes, 4 of the 6 VMs ask now for a 4GB bandwidth
     */
    public Set<SatConstraint> getNewBWRequirements(Model mo) {
        Set<SatConstraint> constraints = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            UUID n = nodes.get(i);
            Set<UUID> vmsOnN = mo.getMapping().getRunningVMs(n);
            Iterator<UUID> ite = vmsOnN.iterator();
            for (int j = 0; ite.hasNext() && j < 4; j++) {
                UUID v = ite.next();
                constraints.add(new Preserve(Collections.singleton(v), "bandwidth", 4));
            }
        }
        return constraints;
    }
}