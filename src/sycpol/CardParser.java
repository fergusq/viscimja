package sycpol;

import sycpol.Sycpol;
import java.util.List;

public class CardParser {
    private String str;

    CardParser() {
	this.str = "";
    }
    
    public void loadCard(List<String> lines) {
	loadCard(lines, 22, 22, "STANDARD SYSTEM CARD I");
    }

    public void loadCard(List<String> lines, int width, int height, String model) {
	
	for (String line : lines) {
	    if (line.length() > width)
		Sycpol.exit("SYNTAX ERROR: INCOMPATIBLE CARD WIDTH.");
	}
	
	// Check card height
	if (lines.size() > height)
	    Sycpol.exit("SYNTAX ERROR: INCOMPATIBLE CARD HEIGHT.");
	
	loadTape(lines, width, "CARD", model);
    }

    public void loadTape(List<String> lines) {
	loadTape(lines, 22, "TAPE", "STANDARD SYSTEM TAPE I");
    }
    
    public void loadTape(List<String> lines, int width, String type, String model) {

	if (lines.size() < 2)
	    Sycpol.exit("SYNTAX ERROR: EMPTY CARD");
	
	for (String line : lines) {
	    if (line.matches("[a-z]"))
		Sycpol.exit("SYNTAX ERROR: UNSUPPORTED CHARACTER SET.");
	}
	
	// Check card declaration (PROGRAM CARD or COMMENT CARD)
	if (lines.get(0).length() < width)
	    Sycpol.exit("SYNTAX ERROR: MISSING "+type+" DECLARATION.");
	if (!lines.get(0).endsWith(type))
	    Sycpol.exit("SYNTAX ERROR: MISSING "+type+" DECLARATION.");
	if (!lines.get(0).startsWith("COMMENT")
	    && !lines.get(0).startsWith("PROGRAM"))
	    Sycpol.exit("SCIM ERROR: UNSUPPORTED "+type+" TYPE.");
	
	// Check card model
	if (!lines.get(1).equals(model))
	    Sycpol.exit("SCIM ERROR: UNSUPPORTED "+type+" MODEL.");

	if (lines.get(0).startsWith("COMMENT")) return;
	
	// Load card
	for (int i = 2; i < lines.size(); i++) {
	    String line = lines.get(i);
	    while (line.length() < width) line = line + " ";
	    this.str += line + "\n";
	}
	String line = "";
	while (line.length() < width) line = line + " ";
	this.str += line + "\n";
    }
    
    public String getCode() {
	return this.str;
    }
}
