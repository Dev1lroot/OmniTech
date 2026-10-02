# Electronics & Circuit Boards

Electric machines are built from **machine casings** and **circuit boards**. A board goes through three stations: you *design* it, *fabricate* it, then *solder and test* it.

## Silicon & Components

**Silicon:** quartz + coke in the *Alloy Furnace* at 1900 °C → metallurgical silicon. With quartz (the crucible) at 1450 °C it grows a boule; steel wire saws it into eight wafers. At 1000 °C a phosphorus mote makes an N-type wafer, a boron mote a P-type wafer, boron on N-type a P-N junction.

**Parts:** junction wafers → diodes and 5.1 V zeners; N/P/N → NPN, P/N/P → PNP transistors. Resistors are crafted blank, then you **paint the colour code**: put the resistor and 3–6 band items in a crafting grid — they are read left to right, top to bottom. Dyes are the digit colours (purple = violet, gray = grey); a gold or silver nugget, dust or mote makes a metallic band. 3 bands: digit, digit, multiplier (±20 %); 4: plus tolerance; 5–6: three digits, multiplier, tolerance (and temperature coefficient). Brown–black–red–gold is 1 kΩ ±5 %. 3–4 band parts are beige carbon film, 5–6 band ones blue metal film. The simulator uses the exact value, and tighter tolerance means more consistent parts. Capacitors come in 100 µF and 1000 µF.

## Ready-made Blueprints

Don't want to design your own? Craft one: **paper + blue dye** plus a **zener diode** (Power Stabilizer), an **NPN transistor** (Power Switch) or a **capacitor** (Clock). Boards made from it arrive at the Soldering Station with every part already placed — load the parts and tin wire, press Solder. JEI shows the full chain and the exact parts list.

## 1 · PCB Workbench

Draw the board on a grid up to 24 × 16.

- **Trace** paints copper, **Pad** drills pin holes, **Shape** adds or cuts board area, **Terminal** names a pad (V+, GND, IN, OUT, AC1, AC2). Right mouse removes.
- Copper joins its four neighbours. Two different signals must never touch — not even side by side.
- Name the board and **Print** it on paper: that blueprint is the photomask.

## 2 · PCB Fabricator

Powered machine. Blueprint in the mask slot (it is not used up), copper plates, and **photoresist** (resin clumps dissolved in acetone in the Solvation Machine) by pipe or bucket. Bigger boards cost more copper and resist.

## 3 · Soldering Station

Insert an etched board, tin wire and parts. Pick a part, rotate it (**R** or wheel), click its centre onto the board — every leg must sit in a pad. Diodes have a cathode band, capacitors a negative stripe, transistors are C on one side, B and E on the other.

**Test** runs the board through every test bench with a real circuit simulation and shows the oscilloscope trace and what passed or failed. **Solder** (shift for a batch) builds it; a board that passes a bench becomes that circuit.

## Test Benches

- **Power Stabilizer** — terminals AC1, AC2, OUT, GND. 12 V AC in, 220 Ω load: needs a steady 4.5–5.8 V, under 0.03 V ripple, drawing evenly from both AC half-waves (a full bridge).
- **Power Switch** — V+, GND, IN, OUT. 12 V rail, 470 Ω load from V+ to OUT: OUT must sit near 12 V while IN is low and drop below 0.5 V once IN goes to 5 V.
- **Clock** — V+, GND, OUT. 9 V supply: OUT must swing at least 5 V and toggle at least four times in five seconds.

[Back to Welcome](page:1)
