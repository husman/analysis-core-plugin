package hudson.plugins.ast.factory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.puppycrawl.tools.checkstyle.TreeWalker;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.FileContents;
import com.puppycrawl.tools.checkstyle.api.FileText;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;

import antlr.RecognitionException;
import antlr.TokenStreamException;

/**
 * Base class for the Abstract Syntax Tree (AST) of the Java files containing a warning.
 *
 * @author Christian M�stl
 */
public abstract class Ast {
    private static final Sha1ToLongConverter SHA_1_TO_LONG_CONVERTER = new Sha1ToLongConverter();

    private final int lineNumber;
    private DetailAST abstractSyntaxTree;

    private String fileName;

    private List<DetailAST> elementsInSameLine;

    private List<DetailAST> children = new ArrayList<DetailAST>();

    private final List<DetailAST> allElements = new ArrayList<DetailAST>();

    private static final String DELIMITER = " ";
    private static final String CHARSET = "UTF-8";
    private static final String HASH_ALGORITHM = "SHA-1";

    /**
     * Creates a new instance of {@link Ast}.
     *
     * @param fileName   the name of the Java file
     * @param lineNumber the line number that contains the warning
     */
    // FIXME: check if we need to provide the line range?
    public Ast(final String fileName, final int lineNumber) {
        this.fileName = fileName;
        this.lineNumber = lineNumber;

        abstractSyntaxTree = createAst(fileName);
        elementsInSameLine = new ArrayList<DetailAST>();
        runThroughAST(abstractSyntaxTree, lineNumber);
        calcConstants(abstractSyntaxTree);
    }

    /**
     * Returns the primary line number of the warning.
     *
     * @return the line number of the warning
     */
    protected int getLineNumber() {
        return lineNumber;
    }

    /**
     * Sets the ast to the specified value.
     *
     * @param abstractSyntaxTree
     *            the value to set
     */
    public void setAbstractSyntaxTree(final DetailAST abstractSyntaxTree) {
        this.abstractSyntaxTree = abstractSyntaxTree;
    }

    /**
     * Returns the DetailAST.
     *
     * @return the DetailAST
     */
    public DetailAST getAbstractSyntaxTree() {
        return abstractSyntaxTree;
    }

    /**
     * Sets the filename to the specified value.
     *
     * @param fileName
     *            the value to set
     */
    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    /**
     * Returns the filename.
     *
     * @return the filename
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the elementsInSameLine to the specified value.
     *
     * @param elementsInSameLine
     *            the value to set
     */
    public void setElementsInSameLine(final List<DetailAST> elementsInSameLine) {
        this.elementsInSameLine = elementsInSameLine;
    }

    /**
     * Clears the elements in same line.
     */
    public void clearElementsInSameLine() {
        elementsInSameLine.clear();
    }

    /**
     * Returns the elementsInSameLine.
     *
     * @return the elementsInSameLine
     */
    public List<DetailAST> getElementsInSameLine() {
        return elementsInSameLine;
    }

    /**
     * Returns the allElements.
     *
     * @return the allElements
     */
    public List<DetailAST> getAllElements() {
        return allElements;
    }

    /**
     * Adds the child to the children.
     *
     * @param child
     *            the child
     */
    public void addChildren(final DetailAST child) {
        children.add(child);
    }

    /**
     * Calculates all children of the given AST-element.
     *
     * @param start
     *            the root of the ast
     * @return all children of the given AST-element
     */
    public List<DetailAST> calcAllChildren(final DetailAST start) {
        if (start != null) {
            children.add(start);
            if (start.getFirstChild() != null) {
                calcAllChildren(start.getFirstChild());
            }
            if (start.getNextSibling() != null) {
                calcAllChildren(start.getNextSibling());
            }
        }
        return children;
    }

    /**
     * Clears the children.
     */
    public void clear() {
        children.clear();
    }

    /**
     * Returns the children.
     *
     * @return the children
     */
    public List<DetailAST> getChildren() {
        return children;
    }

    /**
     * Creates the DetailAST of the specified Java-source-file.
     *
     * @param file
     *            the filename
     * @return the DetailAST
     */
    private DetailAST createAst(final String file) {
        try {
            return TreeWalker.parse(new FileContents(new FileText(new File(file), CHARSET)));
        }
        catch (RecognitionException exception) {
            throw new IllegalArgumentException(exception);
        }
        catch (TokenStreamException exception) {
            throw new IllegalArgumentException(exception);
        }
        catch (IOException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    /**
     * Tests, if two AST are equal. For example permutations of methods are indifferent and therefore the ast returns
     * true.
     *
     * @param ast1
     *            The first AST.
     * @param ast2
     *            The second AST.
     * @return <code>true</code>, if both ast are equal, otherwise return <code>false</code>.
     */
    public boolean isEqualAST(final DetailAST ast1, final DetailAST ast2) {
        return ast1.equalsTree(ast2);
    }

    /**
     * Runs through the AST.
     *
     * @param root
     *            Expects the root of the AST which is run through
     * @param line
     *            the linenumber
     */
    public void runThroughAST(final DetailAST root, final int line) {
        if (root != null) {
            if (root.getLineNo() == line) {
                elementsInSameLine.add(root);
            }
            if (root.getFirstChild() != null) {
                runThroughAST(root.getFirstChild(), line);
            }
            if (root.getNextSibling() != null) {
                runThroughAST(root.getNextSibling(), line);
            }
        }
    }

    /**
     * Runs entirely through the AST.
     *
     * @param root
     *            Expects the root of the AST which is run through
     */
    public void runThroughAST(final DetailAST root) {
        if (root != null) {
            // System.out.println(TokenTypes.getTokenName(root.getType()));
            allElements.add(root);
            isConstant(root);

            if (root.getFirstChild() != null) {
                runThroughAST(root.getFirstChild());
            }
            if (root.getNextSibling() != null) {
                runThroughAST(root.getNextSibling());
            }
        }
    }

    /**
     * Runs entirely through the AST.
     *
     * @param root
     *            Expects the root of the AST which is run through
     */
    public void calcConstants(final DetailAST root) {
        if (root != null) {
            isConstant(root);

            if (root.getFirstChild() != null) {
                calcConstants(root.getFirstChild());
            }
            if (root.getNextSibling() != null) {
                calcConstants(root.getNextSibling());
            }
        }
    }

    /**
     * Prints the list.
     *
     * @param list
     *            List which should be printed.
     */
    public void printList(final List<DetailAST> list) {
        if (list != null) {
            for (DetailAST ast : list) {
                System.out.println(TokenTypes.getTokenName(ast.getType()));
            }
        }
        else {
            System.out.println("Keine Elemente...");
        }
    }

    /**
     * Choose the Area around the warning.
     *
     * @return the Area
     */
    public abstract List<DetailAST> chooseArea();

    /**
     * Depicts the result of chooseArea() as a string.
     *
     * @param delimiter
     *            the delimiter between the ast-elements. If delimiter is equal z, then no delimiter would be inserted.
     * @return the result in string-format
     */
    public String chosenAreaAsString(final char delimiter) {
        List<DetailAST> choosenArea = chooseArea();
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < choosenArea.size(); i++) {
            stringBuilder.append(TokenTypes.getTokenName(choosenArea.get(i).getType()));
            stringBuilder.append(delimiter);
        }

        return stringBuilder.toString();
    }

    /**
     * Prints the result of the chooseArea() on the console.
     *
     * @param delimiter
     *            the delimiter
     */
    public void printChosenArea(final char delimiter) {
        System.out.println(chosenAreaAsString(delimiter));
    }

    /** Necessary for ASTs with name. */
    private String name = "";

    /**
     * Sets the name to the specified value.
     *
     * @param name
     *            the value to set
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the hash code of the selected part of the AST. The hash code is derived from the SHA1 digest.
     *
     * @return hash code of the AST
     */
    public long getContextHashCode() {
        return SHA_1_TO_LONG_CONVERTER.toLong(getDigest());
    }

    /**
     * Returns a digest of the selected part of the AST.
     *
     * @return the digest of the AST, represented as SHA1 hash code
     */
    public String getDigest() {
        List<DetailAST> elements = chooseArea();
        StringBuilder astElements = new StringBuilder();
        children.clear();
        for (DetailAST astElement : elements) {
            int type = astElement.getType();

            if (type == TokenTypes.TYPE) {
                children = calcAllChildren(astElement.getFirstChild());
                for (DetailAST child : children) {
                    astElements.append(child.getText());
                    astElements.append(DELIMITER);
                }
                children.clear();
            }

            boolean lockedNextElement = false;
            if (!constants.isEmpty()) {
                for (DetailAST ast : constants.keySet()) {
                    if (ast.getType() == TokenTypes.IDENT && ast.getText().equals(astElement.getText())) {
                        astElements.append(TokenTypes.getTokenName(constants.get(ast).getType()));
                        astElements.append(DELIMITER);
                        lockedNextElement = true;
                    }
                }
            }
            if (type != TokenTypes.TYPE && !lockedNextElement) {
                astElements.append(TokenTypes.getTokenName(type));
                astElements.append(DELIMITER);
            }
        }

        if (getName() != null) {
            astElements.append(name);
        }

        return createHashCodeFromAst(astElements);
    }

    private String createHashCodeFromAst(final StringBuilder astElements) {
        MessageDigest messageDigest = createMessageDigest();
        byte[] digest = messageDigest.digest(astElements.toString().getBytes(Charset.forName(CHARSET)));

        StringBuilder result = new StringBuilder();
        for (byte b : digest) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private MessageDigest createMessageDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Requested algorithm not found: " + HASH_ALGORITHM);
        }
    }

    private DetailAST objBlock;

    /**
     * Returns the objBlock of the abstract syntax tree.
     *
     * @param topRoot
     *            the highest root of the ast
     * @return the objblock
     */
    protected DetailAST getObjBlock(final DetailAST topRoot) {
        calcObjBlock(topRoot, 0);

        return objBlock;
    }

    /**
     * Calculates the OBJBLOCK of the ast.
     *
     * @param topRoot
     *            the highest root of the ast
     * @param counter
     *            which OBJBLOCK should be found? For example if you want have the first OBJBLOCK, you have to set the
     *            counter to 0.
     */
    protected void calcObjBlock(final DetailAST topRoot, int counter) {
        if (topRoot != null) {
            if (topRoot.getType() == TokenTypes.OBJBLOCK && counter == 0) {
                objBlock = topRoot;
                counter++;
            }
            if (topRoot.getFirstChild() != null) {
                calcObjBlock(topRoot.getFirstChild(), counter);
            }
            if (topRoot.getNextSibling() != null) {
                calcObjBlock(topRoot.getNextSibling(), counter);
            }
        }
    }

    /**
     * Calculates the last sibling of the given ast-element.
     *
     * @param element
     *            the current element in the ast
     * @return the last element.
     */
    protected DetailAST getLastSibling(final DetailAST element) {
        if (element.getNextSibling() != null) {
            return getLastSibling(element.getNextSibling());
        }
        return element;
    }

    /**
     * Calculates the last line number of the abstract syntax tree.
     *
     * @return Returns the last linenumber of the ast.
     */
    public int getLastLineNumber() {
        return getLastSibling(getObjBlock(abstractSyntaxTree).getFirstChild()).getLineNo();
    }

    // Map<nameOfConstant, value>
    private final Map<DetailAST, DetailAST> constants = new HashMap<DetailAST, DetailAST>();

    /**
     * Returns <code>true</code>, if the element is a constant, otherwise returns <code>false</code>.
     *
     * @param element
     *            the element
     * @return <code>true</code>, if the element is a constant, otherwise returns <code>false</code>.
     */
    protected boolean isConstant(final DetailAST element) {
        if (element.getType() != TokenTypes.VARIABLE_DEF) {
            return false;
        }
        else if (element.getChildCount() != 5) {
            return false;
        }

        DetailAST firstChild = element.getFirstChild();
        if (firstChild.getType() != TokenTypes.MODIFIERS || firstChild.getChildCount() != 3) {
            return false;
        }
        DetailAST type = firstChild.getNextSibling();
        if (type.getType() != TokenTypes.TYPE) {
            return false;
        }
        DetailAST ident = type.getNextSibling();
        if (ident.getType() != TokenTypes.IDENT) {
            return false;
        }
        DetailAST assign = ident.getNextSibling();
        if (assign.getType() != TokenTypes.ASSIGN) {
            return false;
        }
        DetailAST semi = assign.getNextSibling();
        if (semi.getType() != TokenTypes.SEMI) {
            return false;
        }

        constants.put(ident, assign.getFirstChild().getFirstChild());

        return true;
    }

    /**
     * Returns the constants.
     *
     * @return the constants
     */
    protected Map<DetailAST, DetailAST> getConstants() {
        return constants;
    }
}