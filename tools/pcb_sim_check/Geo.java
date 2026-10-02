// Run with tools/pcb_sim_check/run.sh — see that script for what this checks.
import com.dev1lroot.mcmods.omnitech.pcb.*;
import com.dev1lroot.mcmods.omnitech.pcb.sim.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public class Geo {
    public static void main(String[] a) throws Exception {
        PcbDesign d = PcbDesign.blank(16, 10).withName("Relay driver");
        int[][] pads = {{0,2},{2,2},{4,2},{5,6},{5,8},{8,3},{7,5},{9,5},{13,2},{13,4},{12,2},{12,8},{13,5}};
        int[][] traces = {{1,2},{5,2},{5,3},{5,4},{5,5},{6,5},{9,6},{9,7},{9,8},{6,8},{7,8},{8,8},{10,8},{11,8},{8,2},{9,2},{10,2},{11,2}};
        for (int[] p : pads) d = d.withCell(p[0], p[1], (byte) (PcbDesign.BOARD | PcbDesign.PAD));
        for (int[] t : traces) d = d.withCell(t[0], t[1], (byte) (PcbDesign.BOARD | PcbDesign.TRACE));
        d = d.withLabel(0,2,"IN").withLabel(12,2,"OUT").withLabel(12,8,"GND").withLabel(13,5,"V+");
        List<PlacedPart> parts = new ArrayList<>();
        PlacedPart[] want = {
            new PlacedPart(PartSpec.RESISTOR, 3, 2, 0, ResistorCode.toCodes(ResistorCode.encode(1000, ResistorCode.Band.GOLD))),
            new PlacedPart(PartSpec.RESISTOR, 5, 7, 1, ResistorCode.toCodes(ResistorCode.encode(10000, ResistorCode.Band.GOLD))),
            new PlacedPart("omnitech:npn_transistor", 8, 4, 0),
            new PlacedPart("omnitech:diode", 13, 3, 1),
        };
        for (PlacedPart p : want) {
            boolean ok = PlacedPart.canPlace(d, parts, p);
            System.out.println("place " + p.itemId() + " -> " + ok + " pins=" + p.pinCells().stream().map(Arrays::toString).toList());
            if (ok) parts.add(p);
        }
        // overlap / off-pad must be rejected
        System.out.println("overlap rejected: " + !PlacedPart.canPlace(d, parts, new PlacedPart("omnitech:diode", 3, 2, 0)));
        System.out.println("off-pad rejected: " + !PlacedPart.canPlace(d, parts, new PlacedPart("omnitech:diode", 3, 5, 0)));
        CircuitGraph g = CircuitGraph.of(d, parts);
        System.out.println("nets=" + g.netCount() + " terminals=" + g.terminals() + " devices=" + g.devices().size());
        for (var dev : g.devices()) System.out.println("  " + dev.spec().itemId() + " " + Arrays.toString(dev.nets()));
        String dir = System.getProperty("benches", "src/main/resources/data/omnitech/circuit_test/");
        for (String f : new String[]{"1_power_stabilizer.json","2_power_switch.json","3_clock_oscillator.json"}) {
            var b = TestBench.fromJson(f, JsonParser.parseString(Files.readString(Path.of(dir + f))).getAsJsonObject());
            var o = TestBenchRunner.run(g, b);
            System.out.println(f + ": applicable=" + o.applicable() + " passed=" + o.passed());
            for (var n : o.notes()) System.out.println("    " + n.key() + " " + Arrays.toString(n.args()));
        }
        // colour code round trips: brown black red gold = 1 kΩ ±5 %, yellow violet black brown brown = 4.7 kΩ ±1 %
        for (double ohms : new double[]{0.47, 1, 10, 100, 470, 1000, 4700, 10000, 47000, 1e6, 2.2e6}) {
            var b = ResistorCode.encode(ohms, ResistorCode.Band.GOLD);
            var v = ResistorCode.decode(b);
            System.out.println("code " + ohms + " -> " + b + " -> " + ResistorCode.format(v.ohms()) + " " + ResistorCode.formatTolerance(v.tolerance())
                    + (Math.abs(v.ohms() - ohms) > 1e-9 * ohms ? "  !!!!!!!! MISMATCH" : ""));
        }
        var five = List.of(ResistorCode.Band.YELLOW, ResistorCode.Band.VIOLET, ResistorCode.Band.BLACK, ResistorCode.Band.BROWN, ResistorCode.Band.BROWN);
        System.out.println("5-band " + five + " -> " + ResistorCode.decode(five));
        System.out.println("invalid gold digit -> " + ResistorCode.decode(List.of(ResistorCode.Band.GOLD, ResistorCode.Band.RED, ResistorCode.Band.RED)));
        System.out.println("invalid black tolerance -> " + ResistorCode.decode(List.of(ResistorCode.Band.RED, ResistorCode.Band.RED, ResistorCode.Band.RED, ResistorCode.Band.BLACK)));
        // rotation sanity: transistor footprint at every rotation keeps 3 distinct pins around the centre
        for (int r = 0; r < 4; r++) System.out.println("rot" + r + " " + new PlacedPart("omnitech:npn_transistor", 0, 0, r).pinCells().stream().map(Arrays::toString).toList());
    }
}
