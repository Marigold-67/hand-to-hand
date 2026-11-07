import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
public class ParseAll {
 public static void main(String[] args)throws Exception{
  var compiler=ToolProvider.getSystemJavaCompiler();var diagnostics=new DiagnosticCollector<JavaFileObject>();
  try(var fm=compiler.getStandardFileManager(diagnostics,null,null)){
   var paths=Files.walk(Path.of(args[0])).filter(p->p.toString().endsWith(".java")).toList();
   var units=fm.getJavaFileObjectsFromPaths(paths);
   var task=(JavacTask)compiler.getTask(null,fm,diagnostics,List.of("-proc:none"),null,units);task.parse();
   for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(d.toString());
   System.out.println("PASS: syntax parsing of "+paths.size()+" Java source files. No Minecraft API/type resolution performed.");
  }
 }
}
