package com.yiji.maven;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * <p>
 * 检查jar中是否有同名的java文件
 * </p>
 * 不检查classpath下非jar包中的java类,相信这个下的类是开发人员故意让它优先加载的
 *
 * ref:http://docs.codehaus.org/display/MAVENUSER/Mojo+Developer+Cookbook#
 * MojoDeveloperCookbook-Gettingdependencyartifactpath
 *
 * @author qzhanbo@yiji.com
 */
@Mojo(name = "checkDuplicateDependency", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public class DuplicateDependencyCheckerMojo extends AbstractMojo {
	
	private static final String DATE_FOMART = "yyyy-MM-dd HH:mm:ss";
	/**
	 * 排除多个子项目中有相同的依赖
	 */
	private static Set<String> fileSet = Sets.newHashSet();
	
	private static Map<String, Artifact> artifactMap = Maps.newConcurrentMap();
	
	/**
	 * 排除某些已知的依赖冲突 比如log4j-over-slf4j jcl-over-slf4j，他的作用是代替log4j和jcl
	 */
	private static Set<String> excludesDeps = Sets.newHashSet(File.separator + "log4j-over-slf4j", File.separator
																									+ "jcl-over-slf4j");
	
	private static volatile boolean dateLoged = false;
	
	/**
	 * 输出日志文件存放路径,考虑到我们的项目大多有多个子项目,就放在命令的执行路径,方便大家查看日志
	 */
	@Parameter(property = "basedir")
	private File basedir;
	
	/**
	 * 发现重复依赖后输出的文件日志
	 */
	private static String checkerLog = "dependency-check.log";
	
	@Component
	private MavenProject project;
	
	@Component
	private ArtifactResolver resolver;
	
	@Parameter(property = "localRepository")
	private ArtifactRepository localRepository;
	
	@Parameter(property = "project.remoteArtifactRepositories")
	private java.util.List remoteRepositories;
	
	/**
	 * 检查jar中是否有同名的java文件 不检查classpath下非jar包中的java类,相信这个下的类是开发人员故意让它优先加载的
	 * 
	 * @throws MojoExecutionException MojoExecutionException
	 */
	public void execute() throws MojoExecutionException {
		getLog().info("检测是否有重复的依赖");
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileWriter(checkerLog, true));
			if (!dateLoged) {
				pw.println("******************************************");
				pw.println(getCurrentDay());
				pw.println("******************************************");
				dateLoged = true;
			}
			//获取所有依赖
			Set artifacts = getAllArtifacts();
			for (Object artifact1 : artifacts) {
				Artifact artifact = (Artifact) artifact1;
				//冲突的包只打印一次日志
				boolean warned = false;
				//检测运行时依赖
				//解析依赖jar包路径
				resolver.resolve(artifact, remoteRepositories, localRepository);
				if (artifact.getFile() == null || !artifact.getFile().getAbsolutePath().endsWith(".jar")) {
					continue;
				}
				String filePath = artifact.getFile().toString();
				//检查是否是已排除的jar
				boolean exclude = false;
				for (String excludesDep : excludesDeps) {
					if (filePath.contains(excludesDep)) {
						exclude = true;
						break;
					}
				}
				if (!exclude && !fileSet.contains(filePath)) {
					fileSet.add(filePath);
					//检查jar包中的每个类文件
					JarFile jarFile = new JarFile(artifact.getFile());
					Enumeration<JarEntry> jarEntrys = jarFile.entries();
					while (jarEntrys.hasMoreElements()) {
						JarEntry jarEntry = jarEntrys.nextElement();
						String filename = jarEntry.getName();
						//只检查类
						if (filename.endsWith(".class")) {
							//判断是否是有效的java类
							if (isValideJava(filename)) {
								Artifact conflictArtifact = artifactMap.get(filename);
								if ((conflictArtifact != null)
									&& (!filePath.equals(conflictArtifact.getFile().toString()))) {
									if (!warned) {
										pw.println("发现冲突:");
										getLog().warn("*********************************************************");
										getLog().warn("*****发现冲突:");
										pw.println("[" + artifact.getScope() + "]" + filePath);
										getLog().warn("*****" + artifact.getFile());
										getLog().warn("*****" + conflictArtifact.getFile());
										pw.println("[" + conflictArtifact.getScope() + "]"
													+ conflictArtifact.getFile().toString());
										getLog().warn("*****" + filename);
										pw.println("冲突类:" + filename);
										getLog().warn("*********************************************************");
										warned = true;
									}
								}
								artifactMap.put(filename, artifact);
							}
						}
					}
				}
				
			}
		} catch (ArtifactResolutionException e) {
			getLog().error("", e);
		} catch (ArtifactNotFoundException e) {
			getLog().error("", e);
		} catch (IOException e) {
			getLog().error("", e);
		} finally {
			if (pw != null) {
				IOUtil.close(pw);
			}
		}
	}
	
	/**
	 * 验证是否是java类,某些打包工具或者某些操作系统会在jar包中遗留些东东(比如ios的_MACOSX目录),跳过这些非法的java文件
	 * 这里并没有完全按照java的类名要求来校验,没有必要
	 * 
	 * @param entryName
	 * @return
	 */
	private static boolean isValideJava(String entryName) {
		if (entryName == null || "".equals(entryName.trim())) {
			return false;
		} else {
			String javaName;
			int idx = entryName.lastIndexOf('/');
			
			if (idx == -1) {
				//没有包的java类
				javaName = entryName;
			} else {
				//有包的java类,获取类名
				javaName = entryName.substring(entryName.lastIndexOf('/') + 1, entryName.length());
			}
			//类名以.或者_开头的,为非法的java名,主要考虑到osx下有隐藏目录
			if (javaName.startsWith(".") || javaName.startsWith("_")) {
				return false;
			} else {
				return true;
			}
		}
	}
	
	private static String getCurrentDay() {
		return new SimpleDateFormat(DATE_FOMART).format(new Date());
	}
	
	/**
	 * 获取所有传递依赖
	 * @return
	 * @throws MojoExecutionException
	 */
	private Set<Artifact> getAllArtifacts() throws MojoExecutionException {
		Set artifacts = project.getDependencyArtifacts();
		ArtifactFilter filter = new ArtifactFilter() {
			public boolean include(Artifact artifact) {
				return artifact != null || !"test".equals(artifact.getScope());
			}
		};
		ArtifactResolutionResult artifactResolutionResult = null;
		try {
			artifactResolutionResult = resolver.resolveTransitively(artifacts, project.getArtifact(), localRepository,
				remoteRepositories, null, filter);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return new HashSet<Artifact>();
		}
		return artifactResolutionResult.getArtifacts();
	}
}
