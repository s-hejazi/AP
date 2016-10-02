package antipatterndetection.handlers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;


public class AntiPatternHandler extends AbstractHandler {

	ASTParser parser = ASTParser.newParser(AST.JLS3);
	//TODO : substitute later, temporary function
	ArrayList <String> dbMethods = new ArrayList<>();
	//
	/*private List<MethodDeclaration> getterMethods = new ArrayList<MethodDeclaration>(); 
	private List<MethodDeclaration> setterMethods = new ArrayList<MethodDeclaration>();*/ 
	private List<String> getterMethodNames = new ArrayList<String>();
	private List<String> setterMethodNames = new ArrayList<String>();

	public static final String GETTER_PREFIX = "get"; 
	public static final String SETTER_PREFIX = "set"; 
	public AntiPatternHandler() {
	}


	public Object execute(ExecutionEvent event) throws ExecutionException {
		//TODO remove later
		dbMethods.add("writeResult");
		dbMethods.add("setVertice");
		//
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		// Get all projects in the workspace
		IProject[] projects = root.getProjects();
		// Loop over all projects
		for (IProject project : projects) {
			try {
				if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {

					IPackageFragment[] packages = JavaCore.create(project).getPackageFragments();
					for (IPackageFragment mypackage : packages) {
						if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
							for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
								// create the AST for the ICompilationUnits
								
								parseForGetterSetter(unit);
								parse(unit);
							}
						}
					}
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		return null;
	}


	/**
	 * Reads a ICompilationUnit and creates the AST DOM for manipulating the
	 * Java source file
	 *
	 * @param unit
	 * @return
	 */

	private void parse(ICompilationUnit unit) {

		parser.setSource(unit);
		parser.setResolveBindings(true);
		fillDbMethodsListWithGetterSetterMethodNames();
		
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor(){
			public boolean visit (ForStatement node) {
				for(String s: dbMethods)
					if(node.getBody().toString().contains(s))
						System.out.println("Instance found in "+ cu.getJavaElement().getElementName()+ ", For loop in line "+cu.getLineNumber(node.getStartPosition()));       		       		
				return true;
			}
			public boolean visit (WhileStatement node) {
				for(String s: dbMethods)
					if(node.getBody().toString().contains(s))
						System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " : While loop in line "+cu.getLineNumber(node.getStartPosition()));       		
				return true;
			}
			public boolean visit (EnhancedForStatement node) {
				for(String s: dbMethods)
					if(node.getBody().toString().contains(s))
						System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " : Enhanced For loop in line "+cu.getLineNumber(node.getStartPosition()));       		
				return true;
			}
			public boolean visit (DoStatement node) {
				for(String s: dbMethods)
					if(node.getBody().toString().contains(s))
						System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " : Do While loop in line "+cu.getLineNumber(node.getStartPosition()));       		
				return true;
			}
		});
	}

	private void parseForGetterSetter(ICompilationUnit unit) {

		parser.setSource(unit);
		parser.setResolveBindings(true);
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor(){
			public boolean visit(MethodDeclaration node) { 
				if(isGetterMethod(node)) 
					getterMethodNames.add(node.getName().getFullyQualifiedName()); 

				if(isSetterMethod(node)) 
					setterMethodNames.add(node.getName().getFullyQualifiedName()); 

				return true; 
			}			
			
		});
	}
	
	private void fillDbMethodsListWithGetterSetterMethodNames(){
		dbMethods.addAll(getterMethodNames);
		dbMethods.addAll(setterMethodNames);
	}

	private boolean isGetterMethod(MethodDeclaration methodDeclaration) { 
		return methodDeclaration.getName().getFullyQualifiedName().startsWith(GETTER_PREFIX); 
	} 

	private boolean isSetterMethod(MethodDeclaration methodDeclaration) { 
		return methodDeclaration.getName().getFullyQualifiedName().startsWith(SETTER_PREFIX); 
	} 
	
	

}

