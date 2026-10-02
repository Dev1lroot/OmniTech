// Run with tools/pcb_sim_check/run.sh — see that script for what this checks.
import com.dev1lroot.mcmods.omnitech.pcb.*;
import com.dev1lroot.mcmods.omnitech.pcb.sim.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

public class Harness {
    static final String DIR = System.getProperty("benches", "src/main/resources/data/omnitech/circuit_test/");
    static TestBench bench(String f) throws Exception {
        return TestBench.fromJson(f, JsonParser.parseString(Files.readString(Path.of(DIR + f))).getAsJsonObject());
    }
    /** "resistor_4k7" etc. become a colour-coded 5 % (gold band) resistor of that value. */
    static final Map<String, Double> OHMS = Map.of("resistor_100r", 100.0, "resistor_470r", 470.0, "resistor_1k", 1000.0,
            "resistor_4k7", 4700.0, "resistor_10k", 10000.0, "resistor_47k", 47000.0);
    static CircuitGraph.Device dev(String id, int... nets) {
        if (OHMS.containsKey(id)) {
            var bands = ResistorCode.encode(OHMS.get(id), ResistorCode.Band.GOLD);
            var v = ResistorCode.decode(bands);
            return new CircuitGraph.Device(PartSpec.of(PartSpec.RESISTOR).orElseThrow().withValue(v.ohms(), v.tolerance()), nets);
        }
        return new CircuitGraph.Device(PartSpec.of("omnitech:" + id).orElseThrow(), nets);
    }
    static CircuitGraph g(int nets, Map<String,Integer> t, CircuitGraph.Device... d) { return new CircuitGraph(nets, new int[0], List.of(d), t); }
    static void report(String name, TestBench b, CircuitGraph g, boolean expect) {
        long t0 = System.nanoTime();
        var o = TestBenchRunner.run(g, b);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("%-34s %s (expected %s) %.0f ms%n", name, o.passed() ? "PASS" : "FAIL", expect ? "PASS" : "FAIL", ms);
        for (var n : o.notes()) System.out.println("    " + (n.ok() ? "ok " : "BAD") + " " + n.key() + " " + Arrays.toString(n.args()));
        if (o.passed() != expect) System.out.println("    !!!!!!!! MISMATCH");
    }
    public static void main(String[] a) throws Exception {
        var stab = bench("1_power_stabilizer.json");
        // AC1=0 AC2=1 VDC=2 GND=3 OUT=4
        var st = Map.of("AC1",0,"AC2",1,"GND",3,"OUT",4);
        report("stabilizer bridge 100R 1000uF zener", stab, g(5, st, dev("diode",0,2), dev("diode",1,2), dev("diode",3,0), dev("diode",3,1),
                dev("capacitor_1000uf",2,3), dev("resistor_100r",2,4), dev("zener_diode",3,4)), true);
        report("stabilizer 470R (starved)", stab, g(5, st, dev("diode",0,2), dev("diode",1,2), dev("diode",3,0), dev("diode",3,1),
                dev("capacitor_1000uf",2,3), dev("resistor_470r",2,4), dev("zener_diode",3,4)), false);
        report("stabilizer 100uF (ripple)", stab, g(5, st, dev("diode",0,2), dev("diode",1,2), dev("diode",3,0), dev("diode",3,1),
                dev("capacitor",2,3), dev("resistor_100r",2,4), dev("zener_diode",3,4)), false);
        report("stabilizer no cap", stab, g(5, st, dev("diode",0,2), dev("diode",1,2), dev("diode",3,0), dev("diode",3,1),
                dev("resistor_100r",2,4), dev("zener_diode",3,4)), false);
        report("stabilizer no zener", stab, g(5, st, dev("diode",0,2), dev("diode",1,2), dev("diode",3,0), dev("diode",3,1),
                dev("capacitor_1000uf",2,3), dev("resistor_100r",2,4)), false);
        report("stabilizer half-wave", stab, g(5, st, dev("diode",0,2), dev("diode",3,1),
                dev("capacitor_1000uf",2,3), dev("resistor_100r",2,4), dev("zener_diode",3,4)), false);
        report("stabilizer zener reversed", stab, g(5, st, dev("diode",0,2), dev("diode",1,2), dev("diode",3,0), dev("diode",3,1),
                dev("capacitor_1000uf",2,3), dev("resistor_100r",2,4), dev("zener_diode",4,3)), false);
        report("stabilizer 2x 1000uF parallel", stab, g(5, st, dev("diode",0,2), dev("diode",1,2), dev("diode",3,0), dev("diode",3,1),
                dev("capacitor_1000uf",2,3), dev("capacitor_1000uf",2,3), dev("resistor_100r",2,4), dev("zener_diode",3,4)), true);

        var pw = bench("2_power_switch.json");
        // V+=0 GND=1 IN=2 OUT=3 B=4
        var pt = Map.of("V+",0,"GND",1,"IN",2,"OUT",3);
        report("switch npn 1k/10k + flyback", pw, g(5, pt, dev("resistor_1k",2,4), dev("resistor_10k",4,1),
                dev("npn_transistor",3,4,1), dev("diode",3,0)), true);
        report("switch 47k base (underdriven)", pw, g(5, pt, dev("resistor_47k",2,4), dev("resistor_10k",4,1),
                dev("npn_transistor",3,4,1), dev("diode",3,0)), false);
        report("switch transistor C/E swapped", pw, g(5, pt, dev("resistor_1k",2,4), dev("resistor_10k",4,1),
                dev("npn_transistor",1,4,3), dev("diode",3,0)), false);
        report("switch resistor only", pw, g(5, pt, dev("resistor_1k",2,3)), false);

        var osc = bench("3_clock_oscillator.json");
        // V+=0 GND=1 OUT=2(Q2C) Q1C=3 Q1B=4 Q2B=5
        var ot = Map.of("V+",0,"GND",1,"OUT",2);
        report("astable 1k/10k/100uF", osc, g(6, ot, dev("resistor_1k",0,3), dev("resistor_1k",0,2), dev("resistor_10k",0,4), dev("resistor_10k",0,5),
                dev("capacitor",3,5), dev("capacitor",2,4), dev("npn_transistor",3,4,1), dev("npn_transistor",2,5,1)), true);
        report("astable 1k/4k7/100uF", osc, g(6, ot, dev("resistor_1k",0,3), dev("resistor_1k",0,2), dev("resistor_4k7",0,4), dev("resistor_4k7",0,5),
                dev("capacitor",3,5), dev("capacitor",2,4), dev("npn_transistor",3,4,1), dev("npn_transistor",2,5,1)), true);
        report("astable 47k/1000uF (too slow)", osc, g(6, ot, dev("resistor_1k",0,3), dev("resistor_1k",0,2), dev("resistor_47k",0,4), dev("resistor_47k",0,5),
                dev("capacitor_1000uf",3,5), dev("capacitor_1000uf",2,4), dev("npn_transistor",3,4,1), dev("npn_transistor",2,5,1)), false);
        report("astable PNP mirror", osc, g(6, Map.of("V+",1,"GND",0,"OUT",2), dev("resistor_1k",0,3), dev("resistor_1k",0,2), dev("resistor_10k",0,4), dev("resistor_10k",0,5),
                dev("capacitor",5,3), dev("capacitor",4,2), dev("pnp_transistor",3,4,1), dev("pnp_transistor",2,5,1)), true);
        report("not cross-coupled", osc, g(6, ot, dev("resistor_1k",0,3), dev("resistor_1k",0,2), dev("resistor_10k",0,4), dev("resistor_10k",0,5),
                dev("capacitor",3,4), dev("capacitor",2,5), dev("npn_transistor",3,4,1), dev("npn_transistor",2,5,1)), false);
    }
}
