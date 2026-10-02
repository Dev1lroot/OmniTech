// Run with tools/pcb_sim_check/run.sh — see that script for what this checks.
import com.dev1lroot.mcmods.omnitech.pcb.*;
import com.dev1lroot.mcmods.omnitech.pcb.sim.*;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Every ready-made blueprint, built with its reference parts, must pass its own bench. */
public class Blueprints {
    static final String DATA = "src/main/resources/data/omnitech/";

    public static void main(String[] a) throws Exception {
        Map<String, TestBench> benches = new TreeMap<>();
        try (var files = Files.list(Path.of(DATA + "circuit_test"))) {
            for (Path f : files.sorted().toList()) {
                String id = f.getFileName().toString().replace(".json", "");
                benches.put(id, TestBench.fromJson(id, JsonParser.parseString(Files.readString(f)).getAsJsonObject()));
            }
        }
        boolean allOk = true;
        try (var files = Files.list(Path.of(DATA + "pcb_blueprint"))) {
            for (Path f : files.sorted().toList()) {
                JsonObject j = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                JsonObject d = j.getAsJsonObject("design");
                String cellStr = d.get("cells").getAsString();
                byte[] cells = new byte[PcbDesign.CELLS];
                for (int i = 0; i < cells.length; i++) cells[i] = (byte) (cellStr.charAt(i) - '0');
                Map<Integer, String> labels = new HashMap<>();
                d.getAsJsonObject("labels").entrySet().forEach(e -> labels.put(Integer.parseInt(e.getKey()), e.getValue().getAsString()));
                PcbDesign design = new PcbDesign(d.get("name").getAsString(), d.get("width").getAsInt(), d.get("height").getAsInt(), cells, labels);

                List<PlacedPart> parts = new ArrayList<>();
                for (JsonElement e : j.getAsJsonArray("parts")) {
                    JsonObject p = e.getAsJsonObject();
                    List<Integer> bands = new ArrayList<>();
                    if (p.has("bands")) for (JsonElement b : p.getAsJsonArray("bands")) bands.add(b.getAsInt());
                    PlacedPart pp = new PlacedPart(p.get("item").getAsString(), p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("rot").getAsInt(), bands);
                    if (!PlacedPart.canPlace(design, parts, pp)) { System.out.println("  !! cannot place " + pp); allOk = false; }
                    parts.add(pp);
                }
                CircuitGraph g = CircuitGraph.of(design, parts);
                String own = j.get("bench").getAsString();
                System.out.println("blueprint " + f.getFileName() + " (" + parts.size() + " parts, " + g.netCount() + " nets)");
                for (var b : benches.values()) {
                    var o = TestBenchRunner.run(g, b);
                    String verdict = !o.applicable() ? "n/a" : o.passed() ? "PASS" : "FAIL";
                    System.out.println("    " + b.id() + ": " + verdict);
                    if (b.id().equals(own) && !o.passed()) {
                        allOk = false;
                        for (var n : o.notes()) System.out.println("        " + n.key() + " " + Arrays.toString(n.args()));
                        System.out.println("    !!!!!!!! MISMATCH — must pass its own bench");
                    }
                }
            }
        }
        System.out.println(allOk ? "all blueprints OK" : "!!!!!!!! MISMATCH in blueprints");
    }
}
