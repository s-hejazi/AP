package antipatterndetection.handlers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchy;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.core.dom.ReturnStatement;

public class AntiPatternHandler extends AbstractHandler {

	ASTParser parser = ASTParser.newParser(AST.JLS8);
	ArrayList <IMethod> dbMethods = new ArrayList<>();
	ArrayList <String> dbClass = new ArrayList<>();
	
	IField[] classVariables;
	String currentMethod;
	Map<String, ArrayList<String>> flaggedLines = new HashMap< String, ArrayList<String>>();

	public AntiPatternHandler() {
	}


	public Object execute(ExecutionEvent event) throws ExecutionException {

		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		IProject[] projects = root.getProjects();
		for (IProject project : projects) {
			try {
				if (project.isOpen() && project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
					IPackageFragment[] packages = JavaCore.create(project).getPackageFragments();
					for (IPackageFragment mypackage : packages) {
						if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
							for (ICompilationUnit unit : mypackage.getCompilationUnits()) {								
								if(entityClass(unit)){
								//IType [] typeDeclarationList = unit.getTypes();						 
								//for (IType typeDeclaration : typeDeclarationList) 
								     //methodList = typeDeclaration.getMethods();
									//TODO : REMOVE 
									//System.out.println( unit.getElementName()+ "\n");									
									getGetterSetter(unit);
									//System.out.println(dbMethods);
									//findDbAccessingMethods(unit); 	
									//resetLists();
							}
						}
					}
					}
				}
			}
 
		catch (CoreException e) {
				e.printStackTrace();
			}
		}
		
		
		for (IProject project : projects) {
			try {
				if (project.isOpen() && project.isNatureEnabled("org.eclipse.jdt.core.javanature")) {
					IPackageFragment[] packages = JavaCore.create(project).getPackageFragments();
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
		try {
			writeResult(flaggedLines);
			System.out.println("analysis finished.");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
	
	boolean isEntityClass;
	private boolean entityClass(ICompilationUnit unit){
/*		isEntityClass = false;
		IType[] typeDeclarationList;
		try {
			typeDeclarationList = unit.getTypes();
			for (IType typeDeclaration : typeDeclarationList)	{	
				//System.out.println(typeDeclaration.getAnnotations());
			     if( typeDeclaration.getAnnotation("Entity") != null)
			     isEntityClass =true;}
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/     
		parser.setSource(unit);
		isEntityClass = false;
		//List <String> annotationList = new ArrayList<String>();
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor() {
		public boolean visit(MarkerAnnotation node) {
		//System.out.println("Class Annotation: " + node.getTypeName().getFullyQualifiedName());
		//annotationList.add(node.getTypeName().getFullyQualifiedName());
			if(node.getTypeName().getFullyQualifiedName().toString().equals("Entity"))
				isEntityClass =true;
		return true;
		}  
/*		public void endVisit(MarkerAnnotation node){
			if (annotationList.contains("Entity"))
				isEntityClass =true;
		}*/
	 });
		
	 return isEntityClass;
	 }
	boolean isColumn = false;
	@SuppressWarnings("restriction")
	private void getGetterSetter(ICompilationUnit unit){
		IType[] typeDeclarationList;
		IAnnotation[] annotations;
		ArrayList <IField> fieldWithoutGet = new ArrayList<>(); 
		ArrayList <IField> fieldWithoutSet = new ArrayList<>(); 
		try {
			typeDeclarationList = unit.getTypes();
			for (IType typeDeclaration : typeDeclarationList){ 		     
			     classVariables = typeDeclaration.getFields();
			     for(IField f : classVariables){
			    	//if(f.getAnnotation("Column")!=null){
			    	annotations = f.getAnnotations();
				 for(int i=0; i<annotations.length; i++){
					if(annotations[i].getElementName().equals("Column")){
						if(GetterSetterUtil.getGetter(f)!= null){
						dbMethods.add(GetterSetterUtil.getGetter(f));
						addCallersToDbMethods(GetterSetterUtil.getGetter(f));
						//System.out.println("get callers of"+ GetterSetterUtil.getGetter(f));
						}
						else{
							fieldWithoutGet.add(f);					
						}
						if(GetterSetterUtil.getSetter(f)!= null){
						dbMethods.add(GetterSetterUtil.getSetter(f));
						addCallersToDbMethods(GetterSetterUtil.getSetter(f));
						//System.out.println("get callers of"+ GetterSetterUtil.getSetter(f));
						}
						else{
							fieldWithoutSet.add(f);	
						}
					}
				}
			}
			}
		} catch (JavaModelException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if(!fieldWithoutGet.isEmpty())
			findGetter(unit, fieldWithoutGet);
		if(!fieldWithoutSet.isEmpty())
			findSetter(unit, fieldWithoutSet);
	}

	public void findGetter(ICompilationUnit unit, ArrayList<IField> fieldWithoutGet){
		parser.setSource(unit);
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor() {
		public boolean visit(MethodDeclaration Mnode){
			Mnode.accept(new ASTVisitor(){
		public boolean visit(ReturnStatement node) {
			for (IField f : fieldWithoutGet)
				///////////////////////
			if(node.getExpression()!= null && node.getExpression().toString().contains(f.getElementName()+" ="))
				try {					
				IMethod m = (IMethod)unit.getElementAt(Mnode.getStartPosition());
				dbMethods.add(m);
				addCallersToDbMethods(m);} 
			catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			return false;													}
			});
			return true;	}
		});
	}
	public void findSetter(ICompilationUnit unit, ArrayList<IField> fieldWithoutSet){
		parser.setSource(unit);
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
		cu.accept(new ASTVisitor() {
		public boolean visit(MethodDeclaration Mnode){
			Mnode.accept(new ASTVisitor(){
				public boolean visit(Assignment node) {
					for (IField f : fieldWithoutSet)
					if(node.getLeftHandSide().toString().equals("this."+f.getElementName()))
					try {
						IMethod m = (IMethod)unit.getElementAt(Mnode.getStartPosition());
						dbMethods.add(m);
						addCallersToDbMethods(m);
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			return false;													}
			});
			return true;	}
		});
	}

	
	int size =0;
	private void addCallersToDbMethods(IMethod m){
		if(size==dbMethods.size())
		return;
		else{
		    size = dbMethods.size();
			HashSet<IMethod> callers = getCallersOf(m);
			for(IMethod i : callers)
				if(!dbMethods.contains(i)){
					dbMethods.add(i);
					addCallersToDbMethods(i);
			}
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

	private void findAntiPatterns(ICompilationUnit unit) throws JavaModelException {
	
		parser.setSource(unit);
		parser.setResolveBindings(true);
		loopNode = false;
		final CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		cu.accept(new ASTVisitor(){
			   public boolean visit (MethodInvocation node) {	
				   	if(loopNode){
							for(IMethod method : dbMethods){
								if(node.getName().toString().equals(method.getElementName())){
									if(node.resolveMethodBinding()!= null 
									   && method.getCompilationUnit()!=null
									   && method.getCompilationUnit().getElementName().equals(		
										node.resolveMethodBinding().getDeclaringClass().getName()+".java" )){
									   //&& method.getParameterTypes().equals(node.arguments()typeArguments())){
										if(!flaggedLines.containsKey(cu.getJavaElement().getElementName())){
										ArrayList <String> lines = new ArrayList<>();
										lines.add(cu.getLineNumber(node.getStartPosition())+ " -> Method " + node.getName().toString());
										flaggedLines.put(cu.getJavaElement().getElementName(), lines);
										//flaggedMethods.add(node.getName().toString());
									}
									else //if(!flaggedLines.get(cu.getJavaElement().getElementName()).contains(cu.getLineNumber(node.getStartPosition()))){
										{
										ArrayList <String> lines = new ArrayList<>();
										lines = flaggedLines.get(cu.getJavaElement().getElementName());
										lines.add(cu.getLineNumber(node.getStartPosition())+ " -> Method " + node.getName().toString());
										flaggedLines.put(cu.getJavaElement().getElementName(), lines);
										//flaggedMethods.add(node.getName().toString());
										//	System.out.println("Instance found in "+cu.getJavaElement().getElementName()+ " in line "+cu.getLineNumber(node.getStartPosition()));       					
								}
								}
						}				
				}}
				return true;
				}
			
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
	public static void writeResult(Map<String, ArrayList<String>> flaggedLines) throws IOException {
		System.out.println("writing started.");
		SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
		Date date = new Date();
		String sDate= sdf.format(date);
		File result = new File("Antipattern detection\\Result " + sDate+ ".txt");
		
		if (!result.exists()) {
			result.createNewFile();
		}
		FileWriter writer = new FileWriter (result.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(writer);
		int count =0;
		for(Map.Entry<String, ArrayList<String>> entry: flaggedLines.entrySet()){
			bw.write("* Class "+ entry.getKey()+ ":\n");
			for(String line:entry.getValue() ){
			bw.write("line "+ line+ " \n");
			count+=1;}
		}
		bw.write(count + " lines flagged in total.");
		bw.close();	
	}
	/*gettersetter
	 * isColumn = false;
	parser.setSource(unit);
	final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
	cu.accept(new ASTVisitor(){
		public boolean visit(NormalAnnotation node) {
			//System.out.println("Annotation: " + node.getTypeName().getFullyQualifiedName());
			if (node.getTypeName().getFullyQualifiedName().equals("Column")){
				isColumn = true;
			}
			return true;
			}
		public boolean visit(VariableDeclarationFragment node){
			
			if(isColumn){
				System.out.println(dbMethods);
			classVariables.add((IField)node);
			try {
				dbMethods.add(GetterSetterUtil.getGetter((IField)node));
				dbMethods.add(GetterSetterUtil.getSetter((IField)node));
				System.out.println(dbMethods);
			} catch (JavaModelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			}
			//TODO : REMOVE
			//System.out.println(node.getName().getIdentifier() + "\n");}
			return false;
		}
		public void endVisit(MarkerAnnotation node) {
			isColumn= false;
			}
	});*/
	/*	private void findDbAccessingMethods(ICompilationUnit unit){
	
	parser.setSource(unit);
	parser.setResolveBindings(true);
	final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
	cu.accept(new ASTVisitor(){
		public boolean visit(MethodDeclaration node) { 
			//TODO REMOVE
			// System.out.println("start method " +  node.getName().getFullyQualifiedName()+ "\n");
			 currentMethod =  node.getName().getIdentifier() ;
			 return true;
		}
		public void endVisit(MethodDeclaration node){
			//TODO : REMOVE
			//System.out.println("end method "+ node.getName().getIdentifier() + "\n");
			currentMethod = "";
		}
		public boolean visit(SimpleName node){
			if (classVariables.contains(node.getIdentifier())){
				for (IMethod method : methodList)
					if(method.getElementName().equals(currentMethod) && !dbMethods.contains(method)){
							dbMethods.add(method);
							dbClass.add(unit.getElementName());
							addCallersToDbMethods(method);
						//TODO : REMOVE
						//System.out.println(currentMethod + " is accessing db because of "+ node.getIdentifier()+"\n" );
						}
			}
			return false;
		}
		
	});		
	}*/
	/*	private void resetLists(){
	//classVariables.clear();		
	methodList = null;
}*/

}

