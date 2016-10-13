package antipatterndetection.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchy;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;

public class AntiPatternHandler extends AbstractHandler {

	ASTParser parser = ASTParser.newParser(AST.JLS3);
	ArrayList <IMethod> dbMethods = new ArrayList<>();
	List <String> classVariables = new ArrayList<String>();
	String currentMethod;
	IMethod [] methodList;
	Map<String, ArrayList<Integer>> flaggedLines = new HashMap< String, ArrayList<Integer>>();
	public AntiPatternHandler() {
	}


	public Object execute(ExecutionEvent event) throws ExecutionException {

		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		IProject[] projects = root.getProjects();
		for (IProject project : projects) {
			try {
				if (project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
					IPackageFragment[] packages = JavaCore.create(project).getPackageFragments();
					for (IPackageFragment mypackage : packages) {
						if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
							for (ICompilationUnit unit : mypackage.getCompilationUnits()) {								
								parser.setSource(unit);
								if(entityClass()){
								IType [] typeDeclarationList = unit.getTypes();						 
								for (IType typeDeclaration : typeDeclarationList) 
								     // get methods list
								     methodList = typeDeclaration.getMethods();
								   //TODO : REMOVE     
									//System.out.println("checking class "+ unit.getElementName()+ "\n");									
									getUnitVariables(unit);
									findDbAccessingMethods(unit); 
									resetLists();
							}
						}
					}
					}
			for (IPackageFragment mypackage : packages) 
					if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) 
						for (ICompilationUnit unit : mypackage.getCompilationUnits()) 
							findAntiPatterns(unit);					
				}
			} 
		catch (CoreException e) {
				e.printStackTrace();
			}
		}
		//TODO
		//make in a report format
		System.out.println(flaggedLines.size()+ " lines flagged:");;
			System.out.println(flaggedLines);
		//	System.out.println(dbMethods.size());
			
		return null;
	}
	
	boolean isEntityClass;
	private boolean entityClass(){
		isEntityClass = false;
		List <String> annotationList = new ArrayList<String>();
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor() {
		public boolean visit(MarkerAnnotation node) {
		//System.out.println("Annotation: " + node.getTypeName().getFullyQualifiedName());
		annotationList.add(node.getTypeName().getFullyQualifiedName());
		return true;
		}  
		public void endVisit(MarkerAnnotation node){
			if (annotationList.contains("Entity"))
				isEntityClass =true;
		}
	 });
		
	 return isEntityClass;
	 }
	private void getUnitVariables(ICompilationUnit unit){
		parser.setSource(unit);
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor(){
			public boolean visit(VariableDeclarationFragment node){
				classVariables.add(node.getName().getIdentifier());
				//TODO : REMOVE
				//System.out.println(node.getName().getIdentifier() + "\n");
				return true;
			}
		});
	}

	private void findDbAccessingMethods(ICompilationUnit unit){
	parser.setSource(unit);
	parser.setResolveBindings(true);
	final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
	cu.accept(new ASTVisitor(){
		public boolean visit(MethodDeclaration node) { 
			//TODO REMOVE
			 //System.out.println("start method " +  node.getName().getFullyQualifiedName()+ "\n");
			 currentMethod =  node.getName().getIdentifier() ;
			 return true;
		}
		public void endVisit(MethodDeclaration node){
			//TODO : REMOVE
			//System.out.println("end method "+ node.getName().getIdentifier() + "\n");
			
		}
		public boolean visit(SimpleName node){
			if (classVariables.contains(node.getIdentifier())){
				for (IMethod method : methodList)
					if(method.getElementName().equals(currentMethod) && !dbMethods.contains(method)){
							dbMethods.add(method);
						addCallersToDbMethods(method);
						//TODO : REMOVE
						//System.out.println(currentMethod + " is accessing db becasue of "+ node.getIdentifier()+"\n" );
						}
			}
			return true;
		}
		
	});		
	}

	private void resetLists(){
	classVariables.clear();
	methodList = null;
}

	private void addCallersToDbMethods(IMethod m){
	    HashSet<IMethod> callers = getCallersOf(m);
		for(IMethod i : callers){
			if(!dbMethods.contains(i))
				dbMethods.add(i);
		}
	}

@SuppressWarnings("restriction")
	private HashSet<IMethod> getCallersOf(IMethod m) {
	CallHierarchy callHierarchy = CallHierarchy.getDefault();	 
	 IMember[] members = {m};	 
	 MethodWrapper[] methodWrappers = callHierarchy.getCallerRoots(members);
	  HashSet<IMethod> callers = new HashSet<IMethod>();
	  for (MethodWrapper mw : methodWrappers) {
	    MethodWrapper[] mw2 = mw.getCalls(new NullProgressMonitor());
	    HashSet<IMethod> temp = getIMethods(mw2);
	    callers.addAll(temp);    
	   }	 
	return callers;
	}
	 
	 @SuppressWarnings("restriction")
	HashSet<IMethod> getIMethods(MethodWrapper[] methodWrappers) {
	  HashSet<IMethod> c = new HashSet<IMethod>(); 
	  for (MethodWrapper m : methodWrappers) {
	   IMethod im = getIMethodFromMethodWrapper(m);
	   if (im != null) {
	    c.add(im);
	   }
	  }
	  return c;
	 }
	 
	 @SuppressWarnings("restriction")
	IMethod getIMethodFromMethodWrapper(MethodWrapper m) {
	  try {
	   IMember im = m.getMember();
	   if (im.getElementType() == IJavaElement.METHOD) {
	    return (IMethod)m.getMember();
	   }
	  } catch (Exception e) {
	   e.printStackTrace();
	  }
	  return null;
	 }
	 

		boolean loopNode; 

	private void findAntiPatterns(ICompilationUnit unit) {
		parser.setSource(unit);
		parser.setResolveBindings(true);
		loopNode = false;
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor(){
			
			public boolean visit (ForStatement node) {
				loopNode = true;
				return true;
			}
			public boolean visit (WhileStatement node) {
				loopNode = true;
				return true;
			}
			public boolean visit (EnhancedForStatement node) {
				loopNode = true;
				return true;
			}
			public boolean visit (DoStatement node) {
				loopNode = true;
			/*	for(IMethod method : dbMethods)
					if(node.getBody().toString().contains(method.getElementName())){
						System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " : Do While loop in line "+cu.getLineNumber(node.getStartPosition()));       		
				totalAntipatternCount++;}*/
				return true;
			}
			
			public boolean visit (MethodInvocation node) {			
				if(loopNode)
				for(IMethod method : dbMethods){
					//if(node.resolveMethodBinding().getKey().equals(method.getKey())){
					if(node.getName().toString().equals(method.getElementName()))
						if(!flaggedLines.containsKey(cu.getJavaElement().getElementName())){
							ArrayList <Integer> lines = new ArrayList<>();
							lines.add(cu.getLineNumber(node.getStartPosition()));
							flaggedLines.put(cu.getJavaElement().getElementName(), lines);
						}
						else if(!flaggedLines.get(cu.getJavaElement().getElementName()).contains(cu.getLineNumber(node.getStartPosition()))){
							ArrayList <Integer> lines = new ArrayList<>();
							lines = flaggedLines.get(cu.getJavaElement().getElementName());
							lines.add(cu.getLineNumber(node.getStartPosition()));
							flaggedLines.put(cu.getJavaElement().getElementName(), lines);
					//	System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " in line "+cu.getLineNumber(node.getStartPosition()));       					
					}
					}
				return true;
			}
			public void endVisit (ForStatement node) {
				loopNode = false;
			}
			public void endVisit (WhileStatement node) {
				loopNode = false;	
			}
			public void endVisit (EnhancedForStatement node) {
				loopNode = false;	
			}
			public void endVisit (DoStatement node) {
				loopNode = false;
			}
		});
	}

	
	
	

}

