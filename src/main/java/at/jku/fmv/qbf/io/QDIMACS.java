package at.jku.fmv.qbf.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import at.jku.fmv.qbf.QBF;
import at.jku.fmv.qbf.QBF.*;
import at.jku.fmv.qbf.io.util.*;

/**
 * A class for reading and writing QDIMACS files.
 * <p>
 * See
 * <a href="http://www.qbflib.org/qdimacs.html">
 * http://www.qbflib.org/qdimacs.html
 * </a>
 * for details on the file format.
 *
 * @author phlo
 */
public class QDIMACS {

	// prevent instantiation
	private QDIMACS() {}

	/**
	 * Reads a given QDIMACS file.
	 *
	 * @param file a file {@link Path}
	 * @return the contained {@link QBF}
	 * @throws IOException if the given {@link Path} is not accessible
	 */
	public static QBF read(Path file) throws IOException {

		List<String> lines = Files.lines(file).collect(Collectors.toList());

		if (lines.isEmpty())
			throw new ParserException(file, "file is empty");

		StringTokenizer splitSpace = new StringTokenizer(' ');

		Function<String, QBF> parseLiteral = s ->
			s.startsWith("-")
				? new Not(new Variable(s.substring(1)))
				: new Variable(s);

		Function<String, QBF> parseClause = line -> {

			List<QBF> variables = splitSpace.stream(line)
				.filter(s -> !s.equals("0"))
				.map(parseLiteral)
				.collect(Collectors.toList());

			if (variables.isEmpty())
				throw new ParserException(
					file,
					"missing variables",
					lines.indexOf(line) + 1);

			return variables.size() > 1
				? new Or(variables)
				: variables.get(0);
		};

		// scan pre{amble,fix} - for loop because we want to break early
		int prefixStart = 0, prefixEnd = 0;

		for (String line : lines) {
			if (line.startsWith("c") || line.startsWith("p"))
				prefixStart++;
			else if (line.startsWith("a") || line.startsWith("e"))
				prefixEnd = (prefixEnd == 0 ? prefixStart : prefixEnd + 1);
			else break;
		}

		// parse matrix
		List<QBF> clauses = lines.subList(prefixEnd + 1, lines.size()).stream()
			.filter(s -> !s.isEmpty())
			.map(parseClause)
			.collect(Collectors.toList());

		if (clauses.isEmpty())
			throw new ParserException(file, "missing clauses");

		QBF formula = clauses.size() > 1
			? new And(clauses)
			: clauses.get(0);

		// prepend prefix
		for (int i = prefixEnd; i >= prefixStart; i--) {
			Set<String> variables = splitSpace.stream(lines.get(i))
				.skip(1)
				.filter(s -> !s.equals("0"))
				.collect(Collectors.toSet());

			if (variables.isEmpty())
				throw new ParserException(file, "missing variables", i + 1);

			formula = lines.get(i).startsWith("a")
				? new ForAll(formula, variables)
				: new Exists(formula, variables);
		}

		return formula;
	}

	/**
	 * Writes the given {@link QBF} to a QDIMACS file.
	 *
	 * @param formula a {@link QBF} worth saving
	 * @param file a file {@link Path}
	 * @throws IOException if the given {@link Path} is not accessible
	 */
	public static void write(QBF formula, Path file) throws IOException {
		try (BufferedWriter bw = Files.newBufferedWriter(file)) {
			Consumer<QBF> noCNF = f -> {
				throw new IllegalArgumentException("skeleton not in CNF");
			};
			Consumer<True> illegalTrue = x -> noCNF.accept(x);
			Consumer<False> illegalFalse = x -> noCNF.accept(x);
			Consumer<And> illegalAnd = x -> noCNF.accept(x);
			Consumer<Or> illegalOr = x -> noCNF.accept(x);
			Consumer<ForAll> illegalForAll = x -> noCNF.accept(x);
			Consumer<Exists> illegalExists = x -> noCNF.accept(x);

			StringBuilder buffer = new StringBuilder();

			Set<String> variables = new HashSet<>();

			int[] numClauses = {0};

			QBF[] matrix = {formula};

			Function<Quantifier, String> getVariables = q ->
				q.variables.stream()
					.peek(variables::add)
					.collect(Collectors.joining(" "));

			Function<QBF, String> getVariable = v -> {
				String var = v.isLiteral()
					? v.isNegation()
						? ((Variable) ((Not) v).subformula).name
						: ((Variable) v).name
					: null;

				if (var == null) noCNF.accept(v);

				variables.add(var);
				return v.isNegation() ? "-" + var : var;
			};

			Consumer<String> appendLine = s -> buffer.append(s + " 0\n");

			Consumer<String> appendClause = s -> {
				appendLine.accept(s);
				numClauses[0]++;
				// NOTE: andThen(x -> numClauses[0]++) not working!?
			};

			// append prefix
			formula.streamPrefix().forEach(q -> {
				if (!q.subformula.isQuantifier())
					matrix[0] = q.subformula;

				q.accept(
					f -> appendLine.accept("a " + getVariables.apply(f)),
					e -> appendLine.accept("e " + getVariables.apply(e)));
			});

			// append matrix
			matrix[0].accept(illegalTrue, illegalFalse,
				var -> appendClause.accept(getVariable.apply(var)),
				not -> appendClause.accept(getVariable.apply(not)),
				and -> and.subformulas.stream().forEach(f ->
					f.accept(illegalTrue, illegalFalse,
						var -> appendClause.accept(getVariable.apply(var)),
						not -> appendClause.accept(getVariable.apply(not)),
						illegalAnd,
						or -> appendClause.accept(
							or.subformulas.stream()
								.map(getVariable)
								.collect(Collectors.joining(" "))),
						illegalForAll, illegalExists)),
				illegalOr, illegalForAll, illegalExists
			);

			// write to file
			bw.append("p cnf " + variables.size() + " " + numClauses[0] + "\n");
			bw.append(buffer);
		}
	}
}
