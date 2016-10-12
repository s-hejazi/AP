package antipatterndetection.handlers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
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
	int totalAntipatternCount = 0;
	public AntiPatternHandler() {
	}


	public Object execute(ExecutionEvent event) throws ExecutionException {

		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
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
								parser.setSource(unit);
								if(entityClass()){
								IType [] typeDeclarationList = unit.getTypes();						 
								for (IType typeDeclaration : typeDeclarationList) 
								     // get methods list
								     methodList = typeDeclaration.getMethods();
								   //TODO : REMOVE     
									System.out.println("checking class "+ unit.getElementName()+ "\n");									
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
		System.out.println(totalAntipatternCount);
			//TODO : REMOVE
		System.out.println("DB MEthods:");
		for(IMethod s:dbMethods){
			System.out.println(s.getElementName());
			//
		}

		return null;
	}
	boolean isEntityClass;
	 public boolean entityClass(){
		isEntityClass = false;
		List <String> annotationList = new ArrayList<String>();
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor() {
		public boolean visit(MarkerAnnotation node) {
		//System.out.println("Annotaion: " + node.getTypeName().getFullyQualifiedName());
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
	public void getUnitVariables(ICompilationUnit unit){
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

public void findDbAccessingMethods(ICompilationUnit unit){
	parser.setSource(unit);
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
					//TODO same name?
					if(method.getElementName().equals(currentMethod) && !dbMethods.contains(method)){
							dbMethods.add(method);
							addCallersToDbMethods(method);

						//TODO : REMOVE
							System.out.println(currentMethod + " is accessing db becasue of "+ node.getIdentifier()+"\n" );
						break;
						}
			}
			return true;
		}
		
	});		
	}

public void resetLists(){
	classVariables.clear();
	methodList = null;
}

public void addCallersToDbMethods(IMethod m){
	    HashSet<IMethod> callers = getCallersOf(m);
		for(IMethod i : callers){
			if(!dbMethods.contains(i))
				dbMethods.add(i);
		}
	}

@SuppressWarnings("restriction")
public HashSet<IMethod> getCallersOf(IMethod m) {
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
	 

	 

	private void findAntiPatterns(ICompilationUnit unit) {

		parser.setSource(unit);

		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor(){
/*			public boolean visit(ClassOrInterfaceDeclaration n, A arg){
				
				return true;
			}*/
			public boolean visit (ForStatement node) {
				for(IMethod method : dbMethods)
					if(node.getBody().toString().contains(method.getElementName()))
						System.out.println("Instance found in "+ cu.getJavaElement().getElementName()+ ", For loop in line "+cu.getLineNumber(node.getStartPosition()));       		       		
						totalAntipatternCount++;
				return true;
			}
			public boolean visit (WhileStatement node) {
				for(IMethod method : dbMethods)
					if(node.getBody().toString().contains(method.getElementName()))
						System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " : While loop in line "+cu.getLineNumber(node.getStartPosition()));       		
				totalAntipatternCount++;
				return true;
			}
			public boolean visit (EnhancedForStatement node) {
				for(IMethod method : dbMethods)
					if(node.getBody().toString().contains(method.getElementName()))
						System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " : Enhanced For loop in line "+cu.getLineNumber(node.getStartPosition()));       		
				totalAntipatternCount++;
				return true;
			}
			public boolean visit (DoStatement node) {
				for(IMethod method : dbMethods)
					if(node.getBody().toString().contains(method.getElementName()))
						System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " : Do While loop in line "+cu.getLineNumber(node.getStartPosition()));       		
				totalAntipatternCount++;
				return true;
			}
		});
	}

	
	
	

}

