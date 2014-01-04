#!/bin/python
# This is a simple .py script to preprocess a csv of projects.
# The script assumes that a CSV file exists with at least
# repository_name, repository_url, repository_language fields.
#
# Originally, this CSV files have been created by the following
# Google BigQuery ( https://bigquery.cloud.google.com/ )
# using the githubarchive table:
#
# SELECT repository_name, repository_organization, repository_url,
#       repository_language, repository_created_at 
# FROM [githubarchive:github.timeline] 
# WHERE repository_forks > 1  AND repository_fork = "false" 
# GROUP BY repository_name, repository_url, repository_language,
#      repository_created_at, repository_organization;
#
# Since this query response is fairly big, the query is first 
# materialized into a table and then exported through Google Cloud
# Storage ( https://storage.cloud.google.com ) into a CSV file.
#
# An (unprocessed) projects CSV file may be found at 
# /afs/inf.ed.ac.uk/group/ML/mallamanis/github_projects
#
# Additionally given that there are repositories that become forks
# later (the repository_fork = "true" changed at some point in time)
# it is necessary to exclude some projects. Althought, this
# could have been resolved with a JOIN BigQuery memory limits do
# not allow that. Therefore, a separate query for such projects
# needs to be made. The following query has been used:
#
# SELECT repository_url, (COUNT(repository_url)-SUM(IF(repository_fork="false",1,0))) AS fork_deficit 
# FROM [githubarchive:github.timeline]
# GROUP BY repository_url
# HAVING fork_deficit > 0 ;
#
# Again the query needs to be matrialized and the exported.
#
# Use python -i preprocess.py followed by run() to 
# preprocess list with default parameters.

__author__ = "Miltos Allamanis"

import csv

CSV_STRUCTURE = ["repository_name", "repository_organization",
"repository_url","repository_language","repository_created_at"]

def read_dataset(filename):
  """
  Read the dataset into a dict. First row is header.
  """
  dset_reader = csv.DictReader(open(filename))
  project_list = []
  for line in dset_reader:
    project_list.append(line);
  return project_list
  
def write_dataset(project_list, filename):
  """
  Write a processed dataset into csv.
  """
  dset_writer = csv.DictWriter(open(filename, 'w'), CSV_STRUCTURE)
  for line in project_list:
    dset_writer.writerow(line)

def exclude_urls(project_list, exclusion_set):
  '''
  Remove Repositories in the exclusion_set
  '''
  count = 0
  for project in project_list:
    if project["repository_url"] in exclusion_set:
      project_list.remove(project)
      count += 1
  print 'Excluded '+str(count)+' projects'

def remove_duplicate_URLs(project_list):
  """
  Remove any duplicate project as seen by the project URL.
  Keeps the project the row that has a higher index in the
  project_list.
  """
  projects_urls = {}
  count = 0
  for project in project_list:
    if project["repository_url"] not in projects_urls:
      projects_urls[project["repository_url"]] = project
    else:
      project_list.remove(projects_urls[project["repository_url"]])
      projects_urls[project["repository_url"]] = project
      count += 1
  print "Removed " + str(count) + " lines with duplicate URLs."

def find_duplicate_projects(project_list):
  """
  Find projects with duplicate names
  """
  project_names = {}
  for project in project_list:
    pname = (project["repository_name"] +
      "_" + project["repository_language"])
    if pname not in project_names:
      project_names[pname] = [project]
    else:
      project_names[pname].append(project)
  
  # Print some stats.
  count = 0
  sum = 0
  for name in project_names:
    if len(project_names[name])>1:
      count += 1
      sum += len(project_names[name])
      # print project_names[name]
  print ('There are ' + str(count) + ' duplicates named projects and '
    'on avg there are ' + str(float(sum)/count))
  # TODO(mallamanis): We still need to decide which ones to remove.

def run():
  """
  Run preprocessing with the default parameters.
  """
  pl = read_dataset('projects')
  ex = read_dataset('exclude_prjs')
  excl_urls = set()
  for url in ex:
    excl_urls.add(url["repository_url"])
  remove_duplicate_URLs(pl)
  exclude_urls(pl, excl_urls)
  find_duplicate_projects(pl)  
  write_dataset(pl, 'projects_processed')
