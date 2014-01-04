#!/usr/bin/python

# This Python script generates a Bash script in stdout
# with the appropriate commands to download a set of
# github projects, resolving conflicts with identically named
# projects and adding random delays to avoid spamming GitHub.
#
# Allows to filter set for a specific language
#
# e.g.
# > download_repos.py -l "Java,C++" -i /path/to/project/list > download_script.sh
# > chmod +x download_script.sh
# > ./download_script.sh
#
# See also download_repos.py --help
#
import csv
from optparse import OptionParser
import random
import sys

def read_project_list(filename):
  """
  Read the list into a dict. First row is header.
  """
  dset_reader = csv.DictReader(open(filename))
  project_list = []
  for line in dset_reader:
    project_list.append(line)
  return project_list
  
def filter_languages(project_list, languages_set):
	"""
	Filter the project list to contain only the
	languages in the languages_set
	"""
	filtered_projects = []
	for project in project_list:
		if project["repository_language"] in languages_set:
			filtered_projects.append(project)
	return filtered_projects

def generate_git_links(project_list):
	""" 
	Generate the Git URLs for download from the given project list.
	Return a list of a dict containing the "git_URL" key
	and the "out_directory" that denotes the directory where 
	the repo will be cloned. This directory takes into account
	duplicate names and resolves conflicts by appending a unique 
	number.
	"""
	download_plan = {};
	for project in project_list:
		url = project["repository_url"] + ".git"
		url = url.replace("https://","git://")
		folder = project["repository_name"]
		if folder in download_plan:
			i = 1
			while folder + "_" + str(i) in download_plan:
				i += 1
			folder = folder + "_" + str(i)		
		download_plan[folder]=url
	return download_plan
	
def generate_download_script(download_plan, add_random_delay=True):
	"""
	Print a download bash script, adding random delays
	"""
	for project in download_plan:
		print "git clone " + download_plan[project] + " " + project
		if add_random_delay:
			rnd = random.gauss(0, 20)
			if rnd > 0:
				print "sleep " + str(rnd)

def run():
	# Parse command line options
	parser = OptionParser()
	parser.add_option("-l", "--lang", type="string", dest="languages",
					  help="Download only repositories for these languages (comma separated list)")
	parser.add_option("-i", "--input", type="string", dest="input_dir",
					  help="The path to the repository list")

	(options, args) = parser.parse_args()

	if options.input_dir == None:
		print 'No input file given'
		sys.exit(2)
	project_list = read_project_list(options.input_dir)
	
	#Filter Languages, if needed
	if options.languages != None:
		project_list = filter_languages(project_list, set(options.languages.split(',')))
	
	# Get git cloning "plan"
	download_plan = generate_git_links(project_list)
	generate_download_script(download_plan)

if __name__ == "__main__":
	run()

