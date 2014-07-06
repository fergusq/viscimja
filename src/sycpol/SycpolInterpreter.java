package sycpol;

import sycpol.SycpolParser;
import sycpol.SycpolParser.*;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

class SycpolInterpreter {

    public boolean checkTypes(String from, String to) {
	if (from.equals(to)) return true;
	if (from.equals(SycpolParser.INTEGER8)
	    && to.equals(SycpolParser.INTEGER16)) return true;
	if (to.equals(SycpolParser.STREAM)
	    && (
		from.equals(SycpolParser.IOSTREAM)
		|| from.equals(SycpolParser.ISTREAM)
		|| from.equals(SycpolParser.OSTREAM)
		)) return true;
	return false;
    }

    public String simpleType(String typename) {
	if (typename.startsWith("+")) return "LIST";
	if (typename.startsWith("%")) return typename;
	if (typename.equals(SycpolParser.INTEGER16)
	    || typename.equals(SycpolParser.INTEGER8))
	    return "INTEGER";
	if (typename.equals(SycpolParser.STRING32)
	    || typename.equals(SycpolParser.STRING16)
	    || typename.equals(SycpolParser.STRING8))
	    return "STRING";
	if (typename.equals(SycpolParser.IOSTREAM)
	    || typename.equals(SycpolParser.ISTREAM)
	    || typename.equals(SycpolParser.OSTREAM)
	    || typename.equals(SycpolParser.STREAM))
	    return "STREAM";
	return typename;
    }

    public class SycpolObject {
	private HashMap<String, SycpolObject> fields = new HashMap<>();
	private ArrayList<SycpolObject> list = new ArrayList<>();
	private String value_str;
	private byte value_i8;
	private short value_i16;
	private OutputStream value_out;
	private InputStream value_in;

	private String type;
	private Structure struct;
	private HashMap<String, String> fieldtypes = new HashMap<>();

	SycpolObject(short val) {
	    this.type = SycpolParser.INTEGER16;
	    this.value_i16 = val;
	}

	SycpolObject(byte val) {
	    this.type = SycpolParser.INTEGER8;
	    this.value_i8 = val;
	}

	SycpolObject(String type, String val) {
	    this.type = type;
	    this.value_str = val;
	    if (type.equals(SycpolParser.STRING8)
		&& val.length() > 8)
		Sycpol.exit("STORAGE ERROR: STRING OVERFLOW.");
	    if (type.equals(SycpolParser.STRING16)
		&& val.length() > 16)
		Sycpol.exit("STORAGE ERROR: STRING OVERFLOW.");
	    if (type.equals(SycpolParser.STRING32)
		&& val.length() > 32)
		Sycpol.exit("STORAGE ERROR: STRING OVERFLOW.");
	}

	SycpolObject(String type, InputStream in, OutputStream out) {
	    this.type = type;
	    this.value_in = in;
	    this.value_out = out;
	    if (type.equals(SycpolParser.ISTREAM)
		&& in == null)
		Sycpol.exit("TYPE ERROR: CLOSED INPUT STREAM.");
	    if (type.equals(SycpolParser.ISTREAM)
		&& out != null)
		Sycpol.exit("TYPE ERROR: OPEN OUTPUT STREAM.");
	    if (type.equals(SycpolParser.OSTREAM)
		&& out == null)
		Sycpol.exit("TYPE ERROR: CLOSED OUTPUT STREAM.");
	    if (type.equals(SycpolParser.OSTREAM)
		&& in != null)
		Sycpol.exit("TYPE ERROR: OPEN INPUT STREAM.");
	}

	SycpolObject(String type, Structure struct) {
	    this.type = type;
	    this.struct = struct;
	    for (Field f : struct.fields)
		this.fieldtypes.put(f.name, f.datatype);
	}

	SycpolObject(String type) {
	    this.type = type;
	}

	public boolean isStructure() {
	    return this.type.startsWith("%");
	}

	public boolean isList() {
	    return this.type.startsWith("+");
	}

	public boolean isString() {
	    return this.type.equals(SycpolParser.STRING8)
		|| this.type.equals(SycpolParser.STRING16)
		|| this.type.equals(SycpolParser.STRING32);
	}

	public boolean isInteger() {
	    return this.type.equals(SycpolParser.INTEGER8)
		|| this.type.equals(SycpolParser.INTEGER16);
	}

	public boolean isByte() {
	    return this.type.equals(SycpolParser.INTEGER8);
	}

	public boolean isShort() {
	    return this.type.equals(SycpolParser.INTEGER16);
	}

	public boolean isStream() {
	    return this.type.equals(SycpolParser.STREAM)
		|| this.type.equals(SycpolParser.ISTREAM)
		|| this.type.equals(SycpolParser.OSTREAM)
		|| this.type.equals(SycpolParser.IOSTREAM);
	}

	public boolean isOStream() {
	    return this.type.equals(SycpolParser.STREAM)
		|| this.type.equals(SycpolParser.OSTREAM)
		|| this.type.equals(SycpolParser.IOSTREAM);
	}

	public boolean isIStream() {
	    return this.type.equals(SycpolParser.STREAM)
		|| this.type.equals(SycpolParser.ISTREAM)
		|| this.type.equals(SycpolParser.IOSTREAM);
	}

	public int integerValue() {
	    if (this.type.equals(SycpolParser.INTEGER8)) return this.value_i8;
	    if (this.type.equals(SycpolParser.INTEGER16)) return this.value_i16;
	    return 0;
	}

	public String stringValue() {
	    return this.value_str;
	}

	public SycpolObject getField(String name) {
	    /*String name = index.stringValue();

	    if (!index.isString())
		Sycpol.exit("TYPE ERROR: STRING EXPECTED.");
	    */

	    // fields and lists
	    if (this.isStructure()) {
		if (this.fieldtypes.get(name) == null) {
		    Sycpol.exit("NAME ERROR: FIELD NOT FOUND.", "NAME: " + name, "STRUCTURE: " + this.type);
		}
		return this.fields.get(name);
	    }

	    if (this.isList()) {
		switch (name) {
		case "FIRST":
		    return this.list.get(0);
		case "LAST":
		    return this.list.get(this.list.size()-1);
		case "LENGTH":
		    return new SycpolObject((short) this.list.size());
		}
	    }

	    if (this.isString()) {
		switch (name) {
		case "LENGTH":
		    return new SycpolObject((byte) this.value_str.length());
		}
	    }

	    Sycpol.exit("TYPE ERROR: STRUCTURE EXPECTED.",
			"GOT: " + this.type);
	    return null;
	}

	public void setField(String name, SycpolObject to) {
	    if (!this.isStructure())
		Sycpol.exit("TYPE ERROR: STRUCTURE EXPECTED.",
			    "GOT: " + this.type);

	    if (this.fieldtypes.get(name) == null) {
		Sycpol.exit("NAME ERROR: FIELD NOT FOUND.", "NAME: " + name, "STRUCTURE: " + this.type);
	    }

	    if (!checkTypes(to.type, this.fieldtypes.get(name)))
		Sycpol.exit("TYPE ERROR: " + simpleType(this.fieldtypes.get(name)) + "EXPECTED.",
			    "EXPECTED: " + this.fieldtypes.get(name),
			    "GOT: " + to.type);

	    this.fields.put(name, to);
	}
	
	public SycpolObject getCharAt(SycpolObject at) {
	    SycpolObject in = this;

	    if (!at.isInteger()) Sycpol.exit("TYPE ERROR: INTEGER EXPECTED.",
					     "GOT: " + this.type);
	    if (!in.isString()) Sycpol.exit("TYPE ERROR: STRING EXPECTED.",
					    "GOT: " + this.type);

	    if (at.integerValue() >= in.value_str.length())
		Sycpol.exit("INDEX OVERFLOW ERROR.");

	    return new SycpolObject((byte) in.value_str.charAt(at.integerValue()));
	}

	public SycpolObject getElementAt(SycpolObject at) {
	    SycpolObject in = this;

	    if (!at.isInteger()) Sycpol.exit("TYPE ERROR: INTEGER EXPECTED.",
					     "GOT: " + this.type);
	    if (!in.isList()) Sycpol.exit("TYPE ERROR: STRING EXPECTED.",
					  "GOT: " + this.type);

	    if (at.integerValue() >= in.list.size())
		Sycpol.exit("INDEX OVERFLOW ERROR.");

	    return in.list.get(at.integerValue());
	}

	public void add(SycpolObject val) {
	    if (!this.isList()) Sycpol.exit("TYPE ERROR: LIST EXPECTED.",
					    "GOT: " + this.type);

	    this.list.add(val);
	}

	public SycpolObject remove() {
	    if (!this.isList()) Sycpol.exit("TYPE ERROR: LIST EXPECTED.",
					    "GOT: " + this.type);

	    return this.list.remove(this.list.size()-1);
	}

	public void send(SycpolObject val) {
	    if (!this.isOStream())
		Sycpol.exit("TYPE ERROR: STREAM EXPECTED.",
			    "EXPECTED: " + "INPUT STREAM",
			    "GOT: " + this.type);
	    if (!val.isByte()) Sycpol.exit("TYPE ERROR: INTEGER 8 EXPECTED.");

	    try {
		this.value_out.write(val.integerValue());
	    } catch (IOException ex) {
		Sycpol.exit("IO ERROR: " + ex.getMessage().toUpperCase());
	    }
	}

	public SycpolObject next() {
	    if (!this.isIStream()) Sycpol.exit("TYPE ERROR: STREAM EXPECTED.",
					       "EXPECTED: " + "INPUT STREAM",
					       "GOT: " + this.type);
	    
	    try {
		return new SycpolObject((byte) this.value_in.read());
	    
	    } catch (IOException ex) {
		Sycpol.exit("IO ERROR: " + ex.getMessage().toUpperCase());
		return null;
	    }
	}

	public void increment() {
	    if (!this.isInteger()) Sycpol.exit("TYPE ERROR: INTEGER EXPECTED.",
					       "GOT: " + this.type);
	    if (this.isByte()) this.value_i8++;
	    else this.value_i16++;
	}

	public void decrement() {
	    if (!this.isInteger()) Sycpol.exit("TYPE ERROR: INTEGER EXPECTED.",
					       "GOT: " + this.type);
	    if (this.isByte()) this.value_i8--;
	    else this.value_i16--;
	}

	public SycpolObject copy() {
	    SycpolObject n = new SycpolObject(this.type);
	    n.value_i8 = this.value_i8;
	    n.value_i16 = this.value_i16;
	    n.value_str = this.value_str;
	    n.value_in = this.value_in;
	    n.value_out = this.value_out;
	    n.fields = (HashMap<String, SycpolObject>) this.fields.clone();
	    n.list = (ArrayList<SycpolObject>) this.list.clone();
	    return n;
	}

	public void change(SycpolObject to) {
	    if (!checkTypes(to.type, this.type))
		Sycpol.exit("TYPE ERROR: " + simpleType(this.type) + " EXPECTED.",
			    "EXPECTED: " + this.type,
			    "GOT: " + to.type);

	    this.value_i8 = to.value_i8;
	    this.value_i16 = to.value_i16;
	    this.value_str = to.value_str;
	    this.value_in = to.value_in;
	    this.value_out = to.value_out;
	    this.fields = to.fields;
	    this.list = to.list;
	    this.struct = to.struct;
	    this.fieldtypes = to.fieldtypes;
	}

	public boolean equals(SycpolObject obj) {
	    if (this.isInteger() && obj.isInteger())
		return this.integerValue() == obj.integerValue();
	    if (this.isString() && obj.isString())
		return this.stringValue().equals(obj.stringValue());
	    return this == obj;
	}
    }

    String stringDeclaration(Declaration decl) {
	SycpolObject obj = decl
	    .createObject(SycpolInterpreter.this);
	if (!obj.isString())
	    Sycpol.exit("TYPE ERROR: STRING EXPECTED.");
	return obj.stringValue();
    }

    public Program sycpolProgram;
    public Map<String, SycpolModule> modules = new HashMap<>();

    public class SycpolModule {
	public Map<String, SycpolObject> variables = new HashMap<>();
	public SycpolParser.Module module;

	SycpolModule(SycpolParser.Module module) {
	    this.module = module;
	    // System.err.println(module.variables);
	    for (int i = 0; i < module.variables.size(); i++) {
		this.variables.put(module.variables.get(i).name,
				   module.variables
				   .get(i)
				   .createObject(SycpolInterpreter.this));
	    }
	}

	Module getEModule(String name) {

	    for (Module emod : sycpolProgram.modules.values()) {
		if (stringDeclaration(SycpolParser
				      .getDeclaration(emod.info,
						      "IDENTIFIER"))
		    .equals(name)) return emod;
	    }
	    
	    Sycpol.exit("NAME ERROR: MODULE NOT FOUND.", "NAME: " + name);
	    return null;
	}

	Procedure getProcedure(String name) {
	    if (this.module.procedures.get(name) != null) {
		return this.module.procedures.get(name);
	    }
	    else if (this.module.externalProcedures.get(name) != null) {
		ExternalProcedure procedure
		    = this.module.externalProcedures.get(name);
		
		String modulename
		    = stringDeclaration(SycpolParser
					.getDeclaration(procedure.info,
							"MODULE NAME"));
		String procedurename
		    = stringDeclaration(SycpolParser
					.getDeclaration(procedure.info,
							"IDENTIFIER"));

		Module rmodule = this.getEModule(modulename);
		Procedure rprocedure = null;

		for (Procedure eproc : rmodule.procedures.values()) {
		    if (stringDeclaration(SycpolParser
					  .getDeclaration(eproc.info,
							  "IDENTIFIER"))
			.equals(procedurename)) {
			rprocedure = eproc;
			break;
		    }
		}

		if (rprocedure == null)
		    Sycpol.exit("NAME ERROR: PROCEDURE NOT FOUND.", "NAME: " + name);

		return rprocedure;
	    }
	    Sycpol.exit("NAME ERROR: PROCEDURE NOT FOUND.", "NAME: " + name);
	    return null;
	}

	Structure getStructure(String name) {
	    if (this.module.structures.get(name) != null) {
		return this.module.structures.get(name);
	    }
	    else if (this.module.externalStructures.get(name) != null) {
		ExternalStructure structure
		    = this.module.externalStructures.get(name);
		
		String modulename
		    = stringDeclaration(SycpolParser
					.getDeclaration(structure.info,
							"MODULE NAME"));
		String structurename
		    = stringDeclaration(SycpolParser
					.getDeclaration(structure.info,
							"IDENTIFIER"));

		Module rmodule = this.getEModule(modulename);
		Structure rstructure = null;

		for (Structure estruc : rmodule.structures.values()) {
		    if (stringDeclaration(SycpolParser
					  .getDeclaration(estruc.info,
							  "IDENTIFIER"))
			.equals(structurename)) {
			rstructure = estruc;
			break;
		    }
		}

		if (rstructure == null)
		    Sycpol.exit("NAME ERROR: STRUCTURE NOT FOUND.", "NAME: " + name);

		return rstructure;
	    }
	    Sycpol.exit("NAME ERROR: STRUCTURE NOT FOUND.", "NAME: " + name);
	    return null;
	}

	void proceed(String name, List<SycpolObject> args) {
	    proceedTo(getProcedure(name), args);
	}

	private String next(List<String> in) {
	    return in.remove(0);
	}

	private String seek(List<String> in) {
	    return in.get(0);
	}

	private void accept(String expected, List<String> in) {
	    String token = next(in);
	    if (!token.equals(expected)) {
		Sycpol.exit("SYNTAX ERROR: UNEXPECTED TOKEN.", "TOKEN: " + token, "EXPECTED: " + expected);
	    }
	}

	void proceedTo(Procedure proc, List<SycpolObject> args) {
	    if (Sycpol.debug) System.err.println("PROCEEDING TO " + proc.name);

	    Map<String, SycpolObject> localvariables = new HashMap<>();

	    for (int i = 0; i < proc.variables.size(); i++) {
		localvariables.put(proc.variables.get(i).name,
				   proc.variables
				   .get(i)
				   .createObject(SycpolInterpreter.this));
	    }

	    if (args.size() != proc.parameters.size())
		Sycpol.exit("ARGUMENT ERROR: WRONG NUMBER OF ARGUMENTS.", "GOT: " + args.size(), "REQUIRED: " + proc.parameters.size());
	    for (int i = 0; i < proc.parameters.size(); i++) {
		localvariables.put(proc.parameters.get(i).name, args.get(i));
	    }

	    short ip = 1;
	    while (true) {
		String stmt = proc.statements.get(ip);

		if (stmt == null)
		    Sycpol.exit("PROGRAM ERROR: LINE NOT FOUND.", "LINE: " + ip);

		if (Sycpol.debug) System.err.println("(" + ip + ") " + stmt);

		List<String> in = new ArrayList<>(Arrays.asList(stmt.split(" ")));
		for (int i = 0; i < in.size(); i++) {
		    while (in.get(i).trim().length() == 0) in.remove(i);
		    if (in.get(i).indexOf('=') != 0) {
			int j = i;
			for (String tok : Arrays.asList(in.remove(i).split("="))) {
			    in.add(j++, "=");
			    in.add(j++, tok);
			}
			in.remove(i);
		    }
		}
		in.add("( EOF )");
		
		switch (seek(in)) {
		case "CHANGE": {
		    accept("CHANGE", in);
		    if (seek(in).equals("THE")) {
			accept("THE", in);
			String field = next(in);
			accept("OF", in);
			SycpolObject obj2 = expr(in, localvariables);
			accept("TO", in);
			SycpolObject to = expr(in, localvariables);
			obj2.setField(field, to.copy());
		    }
		    else {
			String var = next(in);
			if (!var.startsWith("$"))
			    Sycpol.exit("SYNTAX ERROR: VARIABLE EXPECTED.", "GOT: " + var);

			accept("TO", in);
			SycpolObject to = expr(in, localvariables);

			SycpolObject obj;
			obj = localvariables.get(var);
			if (obj != null) {
			    obj.change(to.copy());
			}
			else {
			    obj = this.variables.get(var);
			    if (obj == null)
				Sycpol.exit("NAME ERROR: VARIABLE NOT FOUND.", "NAME: " + var);
			    obj.change(to.copy());
			}
		    }
		    
		    break;}
		case "INCREMENT":
		case "DECREMENT": {
		    boolean increment = next(in).equals("INCREMENT");
		    if (seek(in).equals("THE")) {
			accept("THE", in);
			String field = next(in);
			accept("OF", in);
			SycpolObject obj2 = expr(in, localvariables);
			SycpolObject obj = obj2.getField(field);
			if (increment)
			    obj.increment();
			else
			    obj.decrement();
		    }
		    else {
			SycpolObject obj;
			String var = next(in);
			if (!var.startsWith("$"))
			    Sycpol.exit("SYNTAX ERROR: VARIABLE EXPECTED.", "GOT: " + var);
			obj = localvariables.get(var);
			if (obj != null) {
			    if (increment)
				obj.increment();
			    else
				obj.decrement();
			}
			else {
			    obj = this.variables.get(var);
			    if (obj == null)
				Sycpol.exit("NAME ERROR: VARIABLE NOT FOUND.", "NAME: " + var);
			    if (increment)
				obj.increment();
			    else
				obj.decrement();
			}
		    }
		    
		    break;}
		case "SEND": {
		    accept("SEND", in);
		    SycpolObject obj = expr(in, localvariables);
		    accept("TO", in);
		    SycpolObject to = expr(in, localvariables);
		    if (Sycpol.debug) System.err.println(obj.integerValue() + " -> " + to.value_out.toString());
		    to.send(obj);
		    break;}
		case "ADD": {
		    accept("ADD", in);
		    SycpolObject obj = expr(in, localvariables);
		    accept("TO", in);
		    SycpolObject to = expr(in, localvariables);
		    to.add(obj);
		    break;}
		case "REMOVE": {
		    accept("REMOVE", in);
		    accept("FROM", in);
		    SycpolObject from = expr(in, localvariables);
		    from.remove();
		    break;}
		case "JUMP": {
		    accept("JUMP", in);
		    String label = next(in);
		    if (!label.startsWith("(") || !label.endsWith(")")) {
			Sycpol.exit("SYNTAX ERROR: INVALID LABEL.");
		    }
		    try {
			ip = Short.parseShort(label.substring(1, label.length()-1));
			continue;
		    } catch (NumberFormatException ex) {
			Sycpol.exit("SYNTAX ERROR: INVALID LABEL.");
		    }
		    break;}
		case "IF": {
		    accept("IF", in);
		    SycpolObject val1 = expr(in, localvariables);
		    accept("=", in);
		    SycpolObject val2 = expr(in, localvariables);
		    String label = next(in);
		    if (!label.startsWith("(") || !label.endsWith(")")) {
			Sycpol.exit("SYNTAX ERROR: INVALID LABEL.");
		    }
		    try {
			if (val1.equals(val2)) {
			    ip = Short.parseShort(label.substring(1, label.length()-1));
			    continue;
			}
		    } catch (NumberFormatException ex) {
			Sycpol.exit("SYNTAX ERROR: INVALID LABEL.");
		    }
		    break;}
		case "PROCEED": {
		    accept("PROCEED", in);
		    accept("TO", in);
		    String to = next(in);
		    if (!to.startsWith("!"))
			Sycpol.exit("SYNTAX ERROR: INVALID PROCEDURE REFERENCE.");
		    accept("WITH", in);
		    ArrayList<SycpolObject> pargs = new ArrayList<>();
		    while (!seek(in).equals("( EOF )")) pargs.add(expr(in, localvariables));
		    this.proceed(to, pargs);
		    break;}
		case "RETURN": {
		    accept("RETURN", in);
		    accept("WITH", in);
		    if (seek(in).equals("FAILURE"))
			Sycpol.exit("RESULT: PROGRAM FAILURE.");
		    accept("SUCCESS", in);
		    return;
		}
		}
		ip++;
	    }
	}

	private SycpolObject expr(List<String> in, Map<String, SycpolObject> localvariables) {
	    if (seek(in).equals("A")) {
		accept("A", in);
		accept("NEW", in);
		String strname = next(in);
		Structure str = this.getStructure(strname);
		return new SycpolObject(strname);
	    }
	    if (!seek(in).equals("THE")) {
		// System.err.println(localvariables + "" + variables);
		if (localvariables.get(seek(in)) != null)
		    return localvariables.get(next(in));
		else if (variables.get(seek(in)) != null)
		    return variables.get(next(in));
		else Sycpol.exit("NAME ERROR: VARIABLE NOT FOUND.", "NAME: " + seek(in));
	    }

	    accept("THE", in);
	    if (seek(in).equals("NEXT")) {
		accept("NEXT", in);
		accept("FROM", in);
		SycpolObject stream = expr(in, localvariables);
		return stream.next();
	    }
	    if (seek(in).equals("ELEMENT")) {
		accept("ELEMENT", in);
		SycpolObject at = null,
		    ino = null;
		if (seek(in).equals("AT")) {
		    accept("AT", in);
		    at = expr(in, localvariables);
		    accept("IN", in);
		    ino = expr(in, localvariables);
		}
		else {
		    accept("IN", in);
		    ino = expr(in, localvariables);
		    accept("AT", in);
		    at = expr(in, localvariables);
		}
		return ino.getElementAt(at);
	    }
	    if (seek(in).equals("CHARACTER")) {
		accept("CHARACTER", in);
		SycpolObject at = null,
		    ino = null;
		if (seek(in).equals("AT")) {
		    accept("AT", in);
		    at = expr(in, localvariables);
		    accept("IN", in);
		    ino = expr(in, localvariables);
		}
		else {
		    accept("IN", in);
		    ino = expr(in, localvariables);
		    accept("AT", in);
		    at = expr(in, localvariables);
		}
		return ino.getCharAt(at);
	    }
	    String field = next(in);
	    accept("OF", in);
	    SycpolObject of = expr(in, localvariables);
	    return of.getField(field);
	}
    }

    public SycpolInterpreter(SycpolParser.Program prog) {
	this.sycpolProgram = prog;
    }

    public void interpret() {
	SycpolModule mainModule = null;
	for (SycpolParser.Module mod: this.sycpolProgram.modules.values()) {
	    SycpolModule smod = new SycpolModule(mod);
	    modules.put(mod.name, smod);
	    if (mod.procedures.get("MAIN") != null) {
		if (mainModule != null)
		    Sycpol.exit("PROGRAM ERROR: MULTIPLE MAIN PROCEDURES.");
		else mainModule = smod;
	    }
	    //else System.err.println(mod.name+"!");
	}
	
	if (mainModule == null) {
	    if (Sycpol.verbose) System.err.println("NO MAIN PROCEDURE.");
	    return;
	}
	
	interpretModule(mainModule);
    }

    public void interpretModule(SycpolModule module) {
	module.proceed("MAIN", new ArrayList<SycpolObject>());
    }
}